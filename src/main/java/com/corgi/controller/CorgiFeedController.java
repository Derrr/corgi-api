package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.BarActivityDetail;
import com.corgi.entity.VlogDetail;
import com.corgi.service.AliyunVodService;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.CorgiCoupon;
import com.corgi.user.entity.CorgiFeed;
import com.corgi.user.entity.CorgiVlog;
import com.corgi.user.entity.CorgiVlogHot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

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
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private AliyunVodService aliyunVodService;
    @Autowired
    private MQService mqService;

    @GetMapping("get_feeds")
    public JsonResult getFeeds() {
        List<String> feedIds = corgiFeedService.getUnviewFeed(getUserId());
        List<VlogDetail> details = new ArrayList<>();
        for (String feed : feedIds) {
            VlogDetail detail = getVlogDetail(feed, getUserId());
            if (detail != null) {
                details.add(detail);
            }
            corgiFeedService.viewFeed(getUserId(), feed);
            CorgiVlogHot hot = new CorgiVlogHot();
            hot.setActivityId(feed);
            hot.setViewCount(1);
            corgiVlogService.updateHotVlog(hot);
        }
        mqService.refreshFeed(getUserId());
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

    @GetMapping("vlog_detail")
    public JsonResult vlogDetail(@RequestParam("activityId") String activityId) {
        return new JsonResult(getVlogDetail(activityId, getUserId()));
    }

    @GetMapping("browse")
    public JsonResult viewVideo(@RequestParam("activityId") String activityId) {
        if (corgiUtilService.lock("view_" + activityId)) {
            try {
                corgiFeedService.viewFeed(getUserId(), activityId);
                CorgiVlog corgiVlog = new CorgiVlog();
                corgiVlog.setActivityId(activityId);
                corgiVlog.setViewCount(1);
                corgiVlogService.addVlogCount(corgiVlog);
            } finally {
                corgiUtilService.unlock("view_" + activityId);
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
    public JsonResult getVideoInfo(@RequestParam("videoId")String videoId){
        return new JsonResult(aliyunVodService.getVideoUrl(videoId));
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

}
