package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyuncs.vod.model.v20170321.GetAIMediaAuditJobResponse;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.util.RequestUtil;
import com.corgi.entity.*;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.AliyunVodService;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("feed")
public class CorgiFeedController extends BaseController {
    @Reference
    private CorgiFeedService corgiFeedService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiShareService corgiShareService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private AliyunVodService aliyunVodService;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private MQService mqService;

    @GetMapping("get_feeds")
    public JsonResult getFeeds(@RequestParam("pageSize") Integer size) {
        String userId = "1";
        if (hasUserId()) {
            userId = getUserId();
        }
        List<String> feedIds = corgiFeedService.getUnviewFeed(userId, size);
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(feedIds);
        List<CorgiActivityDetail> details = convertDetail(corgiActivities, userId);
        for (String feed : feedIds) {
            corgiFeedService.viewFeed(userId, feed);
        }
        mqService.refreshFeed(userId);
        return new JsonResult(details);
    }

    @GetMapping("get_feeds_by_activity")
    public JsonResult getFeeds(@RequestParam("activityId") String activityId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer size) {
        String userId = "1";
        if (hasUserId()) {
            userId = getUserId();
        }
        List<String> feedIds = corgiFeedService.getFeedByActivityId(activityId, userId, page, size);
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(feedIds);
        List<CorgiActivityDetail> details = convertDetail(corgiActivities, userId);
        return new JsonResult(details);
    }

    @GetMapping("get_follow_vlog")
    public JsonResult getFollowVlog(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiVlog> corgiVlogs = corgiVlogService.getFollowVlog(getUserId(), page, pageSize);
        List<VlogDetail> vlogDetails = new ArrayList<>();
        for (CorgiVlog vlog : corgiVlogs) {
            VlogDetail detail = buildVlogDetail(vlog, getUserId());
            if (detail != null) {
                vlogDetails.add(detail);
            }
        }
        return new JsonResult(vlogDetails);
    }

    @GetMapping("get_user_vlog")
    public JsonResult getUserVlog(@RequestParam("userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiVlog> corgiVlogs = corgiVlogService.getUserVlog(userId, page, pageSize);
        List<VlogDetail> vlogDetails = new ArrayList<>();
        for (CorgiVlog vlog : corgiVlogs) {
            VlogDetail detail = buildVlogDetail(vlog, getUserId());
            if (detail != null) {
                vlogDetails.add(detail);
            }
        }
        return new JsonResult(vlogDetails);
    }

    @GetMapping("get_topic_vlog")
    public JsonResult getTopicVlog(@RequestParam("topicId") String topicId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiVlog> corgiVlogs = corgiVlogService.getTopicVlog(topicId, page, pageSize);
        List<VlogDetail> vlogDetails = new ArrayList<>();
        for (CorgiVlog vlog : corgiVlogs) {
            VlogDetail detail = buildVlogDetail(vlog, getUserId());
            if (detail != null) {
                vlogDetails.add(detail);
            }
        }
        return new JsonResult(vlogDetails);
    }

    @GetMapping("vlog_detail")
    public JsonResult vlogDetail(@RequestParam("activityId") String activityId) {
        return new JsonResult(getVlogDetail(activityId, getUserId()));
    }

    @GetMapping("browse")
    public JsonResult viewVideo(@RequestParam("activityId") String activityId) {
        if (corgiUtilService.lock("view_" + activityId)) {
            try {
                corgiFeedService.viewFeed(getUserId(), activityId);
            } finally {
                corgiUtilService.unlock("view_" + activityId);
            }
        }
        return new JsonResult();
    }

    @PostMapping("callback")
    public JsonResult callback(@RequestBody String jobStr) {
        log.info("callback:{} ", jobStr);
        JSONObject job = JSONObject.parseObject(jobStr);
        String videoId = job.getString("MediaId");
        CorgiVlog vlog = corgiVlogService.getVlogByVideoId(videoId);
        CorgiActivity activity = corgiActivityFeedService.getActivityById(vlog.getActivityId());
        String status = job.getString("Status");
        if ("fail".equals(status)) {
            String code = job.getString("Code");
            String message = job.getString("Message");
            log.info("check fail...{}:{} ", videoId, code + message);
            activity.setCheckStatus(AliyunGreenService.CHECK);
            corgiActivityService.updateCorgiActivityStatus(activity);
        } else {
            JSONObject data = job.getJSONObject("Data");
            String suggestion = data.getString("Suggestion");
            if (!suggestion.equals("normal")) {
                activity.setCheckStatus(AliyunGreenService.FAIL);
                corgiActivityService.updateCorgiActivityStatus(activity);
            }
        }
        return new JsonResult();
    }


    @PostMapping("add_vlog")
    public JsonResult addView(@RequestBody CorgiActivity corgiActivity) {

        CorgiVlog corgiVlog = new CorgiVlog();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (StringUtils.isEmpty(corgiActivity.getCurrentTime())) {
            corgiVlog.setCtime(sdf.format(new Date()));
        } else {
            corgiVlog.setCtime(corgiActivity.getCurrentTime());
            corgiActivity.setCurrentTime(null);
        }
        corgiActivity.setCategory(CorgiActivity.CAT_VIDEO);
        CorgiActivity result = corgiActivityFeedService.addFeedActivity(corgiActivity);
        corgiVlog.setActivityId(result.getId());
        corgiVlog.setUserId(corgiActivity.getUserId());
        corgiVlog.setType(CorgiVlog.TYPE.USER);
        corgiVlog.setStatus(CorgiVlog.STATUS.UNCHECK);
        corgiVlogService.addVlog(corgiVlog);

        corgiUserActivityService.addActivityCreator(corgiActivity.getUserId(), result.getId(), CorgiActivity.CAT_VIDEO);
        return new JsonResult(result.getId());
    }

    @GetMapping("delete_vlog")
    public JsonResult deleteVlog(@RequestParam("activityId") String activityId) {
        CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
        if (hasUserId() && getUserId().equals(activity.getUserId())) {
            corgiVlogService.deleteVlog(activityId);
            corgiUserActivityService.deleteActivity(activityId);
            activity.setStatus(CorgiActivity.DELETED);
            corgiActivityService.updateCorgiActivityStatus(activity);
        }
        return new JsonResult();
    }

    @GetMapping("fail_vlog")
    public JsonResult failVlog(@RequestParam("activityId") String activityId) {
        CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
        if (hasUserId() && getUserId().equals(activity.getUserId())) {
            corgiVlogService.deleteVlog(activityId);
            activity.setCheckStatus("failed");
            corgiActivityService.updateCorgiActivityStatus(activity);
        }
        return new JsonResult();
    }

    @GetMapping("get_video_info")
    public JsonResult getVideoInfo(@RequestParam("videoId") String videoId) {
        return new JsonResult(aliyunVodService.getVideoUrl(videoId));
    }

    @GetMapping("get_video_detail")
    public JsonResult getVideoDetail(@RequestParam("videoId") String videoId) {
        return new JsonResult(aliyunVodService.getVideoInfo(videoId));
    }

    @GetMapping("get_category_info")
    public JsonResult getCategoryInfo() {
        return new JsonResult(aliyunVodService.getVideoCategory(1000260458L));
    }

    @GetMapping("get_bgm_list")
    public JsonResult getBGMList(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize, @RequestParam("cateId") Long cateId) {
        return new JsonResult(aliyunVodService.getVideoList(page, pageSize, cateId));
    }

    @GetMapping("get_upload_token")
    public JsonResult getUploadToken(@RequestParam("title") String title, @RequestParam("fileName") String fileName) {
        return new JsonResult(aliyunVodService.getUploadToken(title, fileName));
    }


    private VlogDetail getVlogDetail(String activityId, String userId) {
        CorgiVlog vlog = corgiVlogService.getVlog(activityId);
        if (vlog != null) {
            return buildVlogDetail(vlog, userId);
        }
        return null;
    }

    private VlogDetail buildVlogDetail(CorgiVlog vlog, String userId) {
        VlogDetail vlogDetail = VlogDetail.createDetail(vlog);
        vlogDetail.setUserDetail(corgiUserService.getUserDetailBasic(vlog.getUserId()));
        vlogDetail.setActivityDetail(corgiActivityFeedService.getActivityById(vlog.getActivityId()));
        vlogDetail.setHasLike(corgiLikeService.countUserLike(vlog.getActivityId(), userId));
        vlogDetail.setShareCount(corgiShareService.countShare(vlog.getActivityId()));
        return vlogDetail;
    }

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList, String userId) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            Iterator<CorgiActivity> it = activityList.iterator();
            while (it.hasNext()) {
                CorgiActivity activity = it.next();
                if (!userId.equals(activity.getUserId()) && "fail".equals(activity.getCheckStatus())) {
                    it.remove();
                    continue;
                }
                activity.setCurrentTime(now);
                Integer height = 0;
                Integer width = 0;

                if (!CollectionUtils.isEmpty(activity.getPics())) {
                    String picUrl = activity.getPics().get(0).getPicUrl();
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    height = picInfo.getHeight();
                    width = picInfo.getWidth();
                }
                Long commentCount = corgiCommentService.countActivityComment(activity.getId());
                List<ActivityLike> users = corgiLikeService.getFollowUser(getUserId(), activity.getId());
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), getUserId());
                Integer shareCount = corgiShareService.countShare(activity.getId());
                ActivityComment activityComment = corgiCommentService.getLastComment(activity.getId(), getUserId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initSize(height, width)
                        .initCommentCount(commentCount)
                        .initLikeUsers(users)
                        .hasLike(hasLike);

                detail.setLastComment(activityComment);
                detail.setShareCount(shareCount);
                if (!StringUtils.isEmpty(activity.getUserId())) {
                    UserDetail userDetail = corgiUserService.getUserDetailBasic(activity.getUserId());
                    detail.setUserDetail(userDetail);
                    detail.setIsFollowed(corgiUserFollowService.isFollowed(userId, activity.getUserId()));
                }
                detailList.add(detail);
            }
        }
        return detailList;
    }

}
