package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.aliyuncs.vod.model.v20170321.GetAIMediaAuditJobResponse;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.util.RequestUtil;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.*;
import com.corgi.service.*;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.rmi.ServerException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AsyncTaskService asyncTaskService;

    @GetMapping("get_feeds")
    public JsonResult getFeeds(@RequestParam("pageSize") Integer size) {
        String userId = "1";
        try {
            if (hasUserId()) {
                userId = getUserId();
            }
            String key = "get_feeds-" + userId;
            if (!redisTemplate.opsForValue().setIfAbsent(key, "1", 1L, TimeUnit.SECONDS)) {
                Thread.sleep(100L);
            }
            if (size > 10) {
                size = 8;
            }
            List<String> feedIds = corgiFeedService.getUnviewFeed(userId, size);
            List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(feedIds);
            List<CorgiActivityDetail> details = convertDetail(corgiActivities, userId);
            for (String feed : feedIds) {
                corgiFeedService.viewFeed(userId, feed);
            }
            mqService.refreshFeed(userId);
            return new JsonResult(details);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @GetMapping("get_user_feeds")
    public JsonResult getUserFeeds(@RequestParam("userId") String userId, @RequestParam("lastTimestamp") Long lastId, @RequestParam("pageSize") Integer size) {
        if (lastId == null) {
            lastId = 0L;
        }
        for (int i = 0; i < 5; i++) {
            Future<List<CorgiActivity>> activityFuture = asyncTaskService.getUserActivity(lastId, userId, size);
            Future<List<ActivityLike>> likeFuture = asyncTaskService.getUserLike(lastId, userId, size);
            Future<List<ActivityComment>> commentFuture = asyncTaskService.getUserComment(lastId, userId, size);
            List<UserActivity> userActivities = new ArrayList<>();
            try {
                userActivities = this.mergeCreate(userActivities, activityFuture.get(), size);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            log.info("user_feeds:{} ", userActivities.size());
            try {
                userActivities = this.mergeComment(userActivities, commentFuture.get(), size);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            log.info("user_feeds:{} ", userActivities.size());
            try {
                userActivities = this.mergeLike(userActivities, likeFuture.get(), size);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            log.info("user_feeds:{} ", userActivities.size());
            userActivities = this.populateUserActivity(userActivities);
            if (!CollectionUtils.isEmpty(userActivities) && userActivities.size() == 1) {
                UserActivity tmp = userActivities.get(0);
                if (tmp.getDetail() == null && tmp.getType() == null) {
                    lastId = tmp.getOptTime();
                    continue;
                }
            }
            return new JsonResult(userActivities);
        }
        return new JsonResult(new ArrayList<>());
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
        if (StringUtils.isEmpty(videoId)) {
            videoId = job.getString("VideoId");
        }
        CorgiVlog vlog = corgiVlogService.getVlogByVideoId(videoId);
        if (vlog == null) {
            return new JsonResult();
        }

        String eventType = job.getString("EventType");
        String status = job.getString("Status");
        if ("AIMediaAuditComplete".equals(eventType) || "CreateAuditComplete".equals(eventType)) {
            CorgiActivity activity = corgiActivityFeedService.getActivityById(vlog.getActivityId());
            if ("fail".equals(status)) {
                String code = job.getString("Code");
                String message = job.getString("Message");
                log.info("check fail...{}:{} ", videoId, code + message);
                activity.setCheckStatus(AliyunGreenService.CHECK);
                corgiActivityService.updateCorgiActivityStatus(activity);
            } else {
                JSONObject data = job.getJSONObject("Data");
                //判断人工审核
                if (data == null) {
                    String suggestion = job.getString("AuditStatus");
                    if ("Normal".equals(suggestion)) {
                        activity.setCheckStatus(AliyunGreenService.PASS);
                        corgiActivityService.updateCorgiActivityStatus(activity);
                        corgiUserActivityService.changeActivityCreator(activity.getId(), "normal");
                    } else {
                        activity.setCheckStatus(AliyunGreenService.FAIL);
                        corgiActivityService.updateCorgiActivityStatus(activity);
                        corgiUserActivityService.changeActivityCreator(activity.getId(), AliyunGreenService.FAIL);
                    }
                } else {
                    String suggestion = data.getString("Suggestion");
                    log.info("check success...{}:{} ", videoId, suggestion);
                    if (!suggestion.equals("pass")) {
                        activity.setCheckStatus(AliyunGreenService.FAIL);
                        corgiActivityService.updateCorgiActivityStatus(activity);
                        corgiUserActivityService.changeActivityCreator(activity.getId(), AliyunGreenService.FAIL);
                    }
                }
            }
        } else if ("SnapshotComplete".equals(eventType)) {
            if ("success".equals(status)) {
                JSONArray snapshots = job.getJSONArray("Snapshots");
                String cover = snapshots.getString(0).split("\\?Expires")[0];
                if (!StringUtils.isEmpty(cover)) {
                    corgiActivityService.updateByColumnn(vlog.getActivityId(), "coverUrl", cover);
                }
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

    private List<UserActivity> populateUserActivity(List<UserActivity> userActivities) {
        if (CollectionUtils.isEmpty(userActivities)) {
            return userActivities;
        }
        HashMap<String, UserActivity> activityMap = new HashMap<>();
        List<String> activityIds = new ArrayList<>();
        Long timestamp = null;
        for (UserActivity userActivity : userActivities) {
            String activityId = userActivity.getDetail().getId();
            activityIds.add(activityId);
            activityMap.put(activityId, userActivity);
            userActivity.setDetail(null);
            timestamp = userActivity.getOptTime();
        }
        List<CorgiActivityDetail> details = convertDetail(corgiActivityService.getActivityByIds(activityIds), getUserId());
        for (CorgiActivityDetail detail : details) {
            UserActivity activity = activityMap.get(detail.getId());
            activity.setDetail(detail);
        }
        Iterator<UserActivity> it = userActivities.iterator();
        while (it.hasNext()) {
            UserActivity activity = it.next();
            if (activity.getDetail() == null) {
                it.remove();
            }
        }
        if (CollectionUtils.isEmpty(userActivities)) {
            UserActivity userActivity = new UserActivity(timestamp);
            userActivities.add(userActivity);
        }
        return userActivities;
    }

    private List<UserActivity> mergeCreate(List<UserActivity> userActivities, List<CorgiActivity> activityList, Integer size) {
        log.info("user_feeds:{} ", activityList);
        if (activityList == null) {
            return userActivities;
        }
        if (CollectionUtils.isEmpty(userActivities)) {
            for (CorgiActivity corgiActivity : activityList) {
                UserActivity activity = new UserActivity(corgiActivity.getId(), corgiActivity.getCreateTime(), UserActivity.CREATE);
                userActivities.add(activity);
            }
        } else {
            for (CorgiActivity corgiActivity : activityList) {
                UserActivity activity = new UserActivity(corgiActivity.getId(), corgiActivity.getCreateTime(), UserActivity.CREATE);
                userActivities = addUserActivity(userActivities, activity, size);
            }
        }
        return userActivities;
    }

    private List<UserActivity> mergeComment(List<UserActivity> userActivities, List<ActivityComment> commentList, Integer size) {
        if (commentList == null) {
            return userActivities;
        }
        if (CollectionUtils.isEmpty(userActivities)) {
            for (ActivityComment comment : commentList) {
                UserActivity activity = new UserActivity(comment.getActivityId(), comment.getCtime(), UserActivity.COMMENT);
                userActivities.add(activity);
            }
        } else {
            for (ActivityComment comment : commentList) {
                UserActivity activity = new UserActivity(comment.getActivityId(), comment.getCtime(), UserActivity.COMMENT);
                userActivities = addUserActivity(userActivities, activity, size);
            }
        }
        return userActivities;
    }

    private List<UserActivity> mergeLike(List<UserActivity> userActivities, List<ActivityLike> likeList, Integer size) {
        if (likeList == null) {
            return userActivities;
        }
        if (CollectionUtils.isEmpty(userActivities)) {
            for (ActivityLike like : likeList) {
                UserActivity activity = new UserActivity(like.getActivityId(), like.getCtime(), UserActivity.LIKE);
                userActivities.add(activity);
            }
        } else {
            for (ActivityLike like : likeList) {
                UserActivity activity = new UserActivity(like.getActivityId(), like.getCtime(), UserActivity.LIKE);
                userActivities = addUserActivity(userActivities, activity, size);
            }
        }
        return userActivities;
    }

    private List<UserActivity> addUserActivity(List<UserActivity> userActivities, UserActivity activity, Integer size) {
        int i = 0;
        boolean added = false;
        for (UserActivity userActivity : userActivities) {
            if (userActivity.lesser(activity) && i < size) {
                userActivities.add(i, activity);
                added = true;
                break;
            }
            i++;
        }
        if (!added) {
            userActivities.add(activity);
        }
        if (userActivities.size() > size) {
            return userActivities.subList(0, size);
        }
        return userActivities;
    }

    private VlogDetail getVlogDetail(String activityId, String userId) {
        CorgiVlog vlog = corgiVlogService.getVlog(activityId);
        if (vlog == null) {
            vlog = new CorgiVlog();
            vlog.setActivityId(activityId);
            vlog.setUserId(userId);
        }
        return buildVlogDetail(vlog, userId);
    }

    private VlogDetail buildVlogDetail(CorgiVlog vlog, String userId) {
        CorgiActivity activity = corgiActivityFeedService.getActivityById(vlog.getActivityId());
        VlogDetail vlogDetail = VlogDetail.createDetail(vlog);
        vlogDetail.setUserDetail(corgiUserService.getUserDetailBasic(vlog.getUserId()));
        vlogDetail.setActivityDetail(activity);
        vlogDetail.setHasLike(corgiLikeService.countUserLike(vlog.getActivityId(), userId));
        vlogDetail.setShareCount(corgiShareService.countShare(vlog.getActivityId()));
        vlogDetail.setLikeCount(corgiLikeService.countActivityLike(vlog.getActivityId()).intValue());
        vlogDetail.setCommentCount(corgiCommentService.countActivityComment(vlog.getActivityId()).intValue());
        if (StringUtils.isEmpty(vlog.getVideoId()) && activity != null) {
            vlogDetail.setVideoId(activity.getVideoId());
            if (!StringUtils.isEmpty(activity.getCreateTime())) {
                vlogDetail.setCtime(activity.getCreateTime().replaceAll("/", "-"));
            }
        }
        return vlogDetail;
    }

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList, String userId) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Long nowTime = System.currentTimeMillis();
            Iterator<CorgiActivity> it = activityList.iterator();
            while (it.hasNext()) {
                CorgiActivity activity = it.next();
                if (StringUtils.isEmpty(activity.getUserId()) || (!userId.equals(activity.getUserId()) && "fail".equals(activity.getCheckStatus()))) {
                    it.remove();
                    continue;
                }
                if (!CorgiActivity.CAT_VIDEO.equals(activity.getCategory()) && CollectionUtils.isEmpty(activity.getPics())) {
                    it.remove();
                    continue;
                }
                if (!userId.equals(activity.getUserId()) && AliyunGreenService.NOT_GOOD.equals(activity.getCheckStatus())) {
                    it.remove();
                    continue;
                }
                UserDetail userDetail = corgiUserService.getUserDetailBasic(activity.getUserId());
                if (userDetail == null) {
                    it.remove();
                    continue;
                }
                activity.setCurrentTime(now);
                Long height = activity.getHeight();
                Long width = activity.getWidth();

                if (!CollectionUtils.isEmpty(activity.getPics()) && (height == null || width == null)) {
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
                Long likeCount = corgiLikeService.countActivityLike(activity.getId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initSize(height, width)
                        .initCommentCount(commentCount)
                        .initLikeUsers(users)
                        .initLikeCount(likeCount)
                        .hasLike(hasLike);
                detail.setTimeShow(TimeUtil.buildTimeText(detail.getCreateTime(), nowTime, sdf));
                detail.setLastComment(activityComment);
                detail.setShareCount(shareCount);
                if (!StringUtils.isEmpty(activity.getUserId())) {
                    detail.setUserDetail(corgiUtilService.checkUserDetail(userDetail, userId));
                    detail.setIsFollowed(corgiUserFollowService.isFollowed(userId, activity.getUserId()));
                }
                detailList.add(detail);
            }
        }
        return detailList;
    }

}
