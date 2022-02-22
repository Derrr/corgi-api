package com.corgi.controller;

import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.VlogDetail;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.CorgiVlog;
import com.corgi.user.entity.CorgiVlogHot;
import com.corgi.user.entity.UserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("hot_vlog")
public class CorgiHotVlogController extends BaseController {
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    @GetMapping("list")
    public JsonResult listHot(@RequestParam(required = false, name = "status") String status, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiVlogHot hot = new CorgiVlogHot();
        hot.setType(CorgiVlogHot.TYPE.MANUAL);
        if (!StringUtils.isEmpty(status)) {
            hot.setStatus(status);
        }
        List<CorgiVlogHot> hots = corgiVlogService.getHotVlog(hot, page, pageSize);
        List<VlogDetail> details = new ArrayList<>();
        for (CorgiVlogHot hot1 : hots) {
            details.add(buildDetail(hot1));
        }
        return new JsonResult(details);
    }

    @GetMapping("count")
    public JsonResult countHot(@RequestParam(required = false, name = "status") String status) {
        CorgiVlogHot hot = new CorgiVlogHot();
        hot.setType(CorgiVlogHot.TYPE.MANUAL);
        if (!StringUtils.isEmpty(status)) {
            hot.setStatus(status);
        }
        return new JsonResult(corgiVlogService.countHotVlog(hot));
    }

    @PostMapping("update")
    public JsonResult updateHot(@RequestBody CorgiVlogHot corgiVlogHot) {
        corgiVlogHot.setViewCount(null);
        corgiVlogHot.setLikeCount(null);
        corgiVlogHot.setActivityId(null);
        corgiVlogService.updateHotVlog(corgiVlogHot);
        return new JsonResult();
    }

    @GetMapping("test_add")
    public JsonResult addHot(@RequestParam("activityId") String activityId) {
        CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
        mqService.sendMessage(buildCreatorMessage(activityId));
        mqService.sendMessage(buildFollowerMessage(activityId, activity.getCategory()));
        return new JsonResult();
    }

    @PostMapping("add")
    public JsonResult addHot(@RequestBody CorgiVlogHot corgiVlogHot) {
        corgiVlogHot.setViewCount(null);
        corgiVlogHot.setLikeCount(null);
        if (corgiVlogHot.getExpectView() == null || corgiVlogHot.getExpectView() <= 0) {
            corgiVlogHot.setExpectView(3000);
        }
        corgiVlogHot.setType(CorgiVlogHot.TYPE.MANUAL);
        corgiVlogService.addHotVlog(corgiVlogHot);
        corgiActivityService.updateByColumn(corgiVlogHot.getActivityId(), "checkStatus", "good");
        CorgiActivity activity = corgiActivityFeedService.getActivityById(corgiVlogHot.getActivityId());
        mqService.sendMessage(buildCreatorMessage(corgiVlogHot.getActivityId()));
        if (redisTemplate.opsForValue().setIfAbsent("hot_add-" + activity.getUserId() + "-" + getUserId(), System.currentTimeMillis() + "", 7L, TimeUnit.DAYS)) {
            mqService.sendMessage(buildFollowerMessage(corgiVlogHot.getActivityId(), activity.getCategory()));
        }
        return new JsonResult();
    }

    private VlogDetail buildDetail(CorgiVlogHot hot) {
        VlogDetail detail = new VlogDetail();
        detail.setActivityId(hot.getActivityId());
        detail.setId(hot.getId());
        detail.setLikeCount(hot.getLikeCount());
        if (hot.getViewCount() != null) {
            detail.setViewCount(hot.getViewCount().longValue());
        }
        detail.setExpectView(hot.getExpectView());
        detail.setCtime(hot.getCtime());
        detail.setStatus(hot.getStatus());
        CorgiActivity activity = corgiActivityFeedService.getActivityById(hot.getActivityId());
        activity.setPics(corgiPicService.getActivityPic(activity.getId()));
        detail.setActivityDetail(activity);
        return detail;
    }


    private PushMessage buildCreatorMessage(String activityId) {
        CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
        PushMessage pushMessage = new PushMessage();
        pushMessage.setSourceUserId("corgihelper");
        pushMessage.setTargetUserId(activity.getUserId());
        pushMessage.setMessage("您的动态被推荐至热门");
        HashMap<String, Object> extra = new HashMap<>();
        extra.put("type", "907");
        extra.put("urlType", "2");
        extra.put("alertTitle", "您的动态被推荐至热门");
        extra.put("url", activityId);
        List<ActivityPic> pics = corgiPicService.getActivityPic(activityId);
        if (CollectionUtils.isNotEmpty(pics)) {
            extra.put("picUrl", pics.get(0).getPicUrl());
        } else if (com.alibaba.dubbo.common.utils.StringUtils.isNotEmpty(activity.getCoverUrl())) {
            extra.put("picUrl", activity.getCoverUrl());
        }
        extra.put("desc", "恭喜呀～你于\"" + activity.getCreateTime() + "\"发布的动态\"" + (activity.getTitle() == null ? "" : activity.getTitle()) + "\"获得了平台推荐，请及时回复粉丝的评论吧！");
        pushMessage.setExtra(extra);
        return pushMessage;
    }

    private PushMessage buildFollowerMessage(String activityId, String category) {
        CorgiActivity activity = corgiActivityFeedService.getActivityById(activityId);
        PushMessage pushMessage = new PushMessage();
        pushMessage.setType(PushMessage.ACTIVITY);
        pushMessage.setSourceUserId("corgihelper");
        pushMessage.setMessage("热门动态提醒");
        pushMessage.setTargetUserId(activity.getUserId());
        UserDetail detail = corgiUserService.getUserDetailBasic(activity.getUserId());
        HashMap<String, Object> extra = new HashMap<>();
        extra.put("type", "907");
        extra.put("urlType", "2");
        extra.put("url", activityId);
        extra.put("alertTitle", "热门动态提醒");
        if (activity.getCoverUrl() != null && !activity.getCoverUrl().contains("?x-oss-process")) {
            if (StringUtils.isEmpty(activity.getVideoId())) {
                extra.put("picUrl", activity.getCoverUrl() + "?x-oss-process=style/fuzzyCover");
            } else {
                extra.put("picUrl", activity.getCoverUrl());
            }
        }
        if (!CorgiActivity.CAT_PAYING.equals(category)) {
            List<ActivityPic> pics = corgiPicService.getActivityPic(activityId);
            if (CollectionUtils.isNotEmpty(pics)) {
                extra.put("picUrl", pics.get(0).getPicUrl());
            }
        }
        extra.put("desc", "你关注的好友" + detail.getNickname() + "发布的付费动态正在被围观快去看看吧！");
        pushMessage.setExtra(extra);
        return pushMessage;
    }
}
