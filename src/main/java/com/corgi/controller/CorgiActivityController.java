package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyuncs.vod.model.v20170321.GetMezzanineInfoResponse;
import com.aliyuncs.vod.model.v20170321.GetVideoInfoResponse;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.*;
import com.corgi.common.JsonResult;
import com.corgi.common.PageResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.util.RequestUtil;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.*;
import com.corgi.entity.tool.AddAttendResult;
import com.corgi.entity.tool.Hashtag;
import com.corgi.exception.PermissionException;
import com.corgi.service.AliyunVodService;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("activity")
public class CorgiActivityController extends BaseController {
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiFavorActivityService corgiFavorActivityService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiAreaService corgiAreaService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiShareService corgiShareService;
    @Reference
    private CorgiBarService corgiBarService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiFeedService corgiFeedService;
    @Reference
    private CorgiOrderService corgiOrderService;
    @Reference
    private CorgiBlacklistService corgiBlacklistService;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AliyunVodService aliyunVodService;
    @Autowired
    private MQService mqService;

    public static final String CALL_CITY_PREFIX = "call_city_";

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        if (redisTemplate.hasKey("darkroom_" + getUserId())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "禁止发布");
        }
        String hashKey = MD5Encoder.encode(activity.getContent().getBytes(StandardCharsets.UTF_8));
        if (!redisTemplate.opsForValue().setIfAbsent("addActivity-" + hashKey + "-" + getUserId(), System.currentTimeMillis() + "", 10L, TimeUnit.MINUTES)) {
            return new JsonResult(Constants.API_ERROR_CODE, "抱歉，同一内容不可重复发布");
        }
        activity.setCategory(CorgiActivity.CAT_ACTIVITY);
        log.info("user {} adding activity", activity.getUserId());
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkActivity(activity);
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getUserId(), CheckPic.ACTIVITY);
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        redisTemplate.delete("activity_count_" + activity.getUserId());
        return new JsonResult(AddActivityResult.getResult(activity)
                .setActivityPics(activityPics));
    }

    @PostMapping("attend")
    public JsonResult attend(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        String now = System.currentTimeMillis() + "";
        if (!redisTemplate.opsForValue().setIfAbsent("activity_attended_" + activity.getUserId(), now, 2L, TimeUnit.SECONDS)) {
            return new JsonResult(Constants.API_ERROR_CODE, "打卡太频繁了哦");
        }
        Integer addResult = -1;
        CorgiActivity searchActivity = new CorgiActivity();
        searchActivity.setUserId(activity.getUserId());
        searchActivity.setBarId(activity.getBarId());
        searchActivity.setCreateTime(new SimpleDateFormat("yyyy/MM/dd").format(new Date()));
        searchActivity.setCategory(CorgiActivity.CAT_ATTENDANCE);
        List<CorgiActivity> result = corgiActivityService.searchCorgiActivity(searchActivity, -1, 1);
        if (CollectionUtils.isEmpty(result)) {
            activity.setCategory(CorgiActivity.CAT_ATTENDANCE);
            activity.setCheckStatus(AliyunGreenService.PASS);
            activity = aliyunGreenService.checkImageActivity(activity);
            activity = corgiActivityService.addCorgiActivity(activity);
            addResult = 0;
        } else {
            activity = result.get(0);
            addResult = 1;
            if (!CollectionUtils.isEmpty(activity.getPics())) {
                addResult = 2;
            }
        }
        return new JsonResult(AddAttendResult.getResult(activity, addResult));
    }

    @GetMapping("get_attendances")
    public JsonResult getAttendance(@RequestParam(name = "date", required = false) String date, @RequestParam("barId") String barId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiActivity search = new CorgiActivity();
        search.setBarId(barId);
        search.setStatus(CorgiActivity.NOT_DELETED);
        if (!StringUtils.isEmpty(date)) {
            search.setCreateTime(date);
        }
        List<CorgiActivity> corgiActivities = corgiActivityService.searchCorgiActivity(search, page, pageSize);
        List<CorgiActivityDetail> details = new ArrayList<>();
        for (CorgiActivity activity : corgiActivities) {
            CorgiActivityDetail detail = new CorgiActivityDetail(activity);
            if (activity.getUserId() != null) {
                detail.setUserDetail(corgiUserService.getUserDetail(activity.getUserId(), null));
            }
            if (!CollectionUtils.isEmpty(detail.getPics()) && !StringUtils.isEmpty(detail.getPics().get(0).getPicUrl())) {
                if (detail.getHeight() == null || detail.getWidth() == null) {
                    String picUrl = detail.getPics().get(0).getPicUrl();
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    Long height = picInfo.getHeight();
                    Long width = picInfo.getWidth();
                    detail.setHeight(height);
                    detail.setWidth(width);
                }
                details.add(detail);
            }
        }
        return new JsonResult(details);
    }

    @GetMapping("get_heat_attendances")
    public JsonResult getHeatAttendance(@RequestParam("barId") String barId) {
        CorgiActivity search = new CorgiActivity();
        search.setBarId(barId);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);
        search.setCategory(CorgiActivity.CAT_ATTENDANCE);
        search.setCreateTime(new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()));
        List<String> activityIds = corgiUserActivityService.getHeatActivity(search, 1, 18);
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivity> result = new ArrayList<>();
        for (CorgiActivity activity : activities) {
            if (!CollectionUtils.isEmpty(activity.getPics()) && !StringUtils.isEmpty(activity.getPics().get(0).getPicUrl())) {
                result.add(activity);
            }
        }
        return new JsonResult(result);
    }


    @PostMapping("add_image_activity")
    public JsonResult addImageActivity(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        if (!redisTemplate.opsForValue().setIfAbsent("activity_sent_" + activity.getUserId(), System.currentTimeMillis() + "", 20L, TimeUnit.SECONDS)) {
            return new JsonResult(Constants.API_ERROR_CODE, "发送太频繁了哦");
        }
        if (redisTemplate.hasKey("darkroom_" + getUserId())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "禁止发布");
        }
        if (!this.checkRate(activity.getUserId(),
                !StringUtils.isEmpty(activity.getVideoId()) ? CorgiActivity.CAT_VIDEO : CorgiActivity.CAT_IMAGE, 5)) {
            return new JsonResult(Constants.API_ERROR_CODE, "一天只能发布5条动态");
        }
        if (StringUtils.isEmpty(activity.getCategory())) {
            activity.setCategory(CorgiActivity.CAT_IMAGE);
        }

        if (!StringUtils.isEmpty(activity.getVideoId())) {
            GetMezzanineInfoResponse response = aliyunVodService.getVideoInfo(activity.getVideoId());
            GetMezzanineInfoResponse.Mezzanine mezzanine = response.getMezzanine();
            if (mezzanine != null) {
                activity.setHeight(mezzanine.getHeight());
                activity.setWidth(mezzanine.getWidth());
                activity.setVideoUrl(mezzanine.getFileURL().split("\\?Expires")[0]);
                GetVideoInfoResponse infoResponse = aliyunVodService.getVideoUrl(activity.getVideoId());
                if (infoResponse != null && infoResponse.getVideo() != null) {
                    if (StringUtils.isEmpty(activity.getCoverUrl())) {
                        activity.setCoverUrl(infoResponse.getVideo().getCoverURL().split("\\?Expires")[0]);
                    }
                    if ("Blocked".equals(infoResponse.getVideo().getAuditStatus())) {
                        activity.setCheckStatus(AliyunGreenService.FAIL);
                    }
                }
            }
            activity.setCategory(CorgiActivity.CAT_VIDEO);
        } else if (CollectionUtils.isEmpty(activity.getPics())) {
            activity.setCategory(CorgiActivity.CAT_TEXT);
        }

        activity.setCheckStatus(AliyunGreenService.PASS);
        activity.setStrictStatus(AliyunGreenService.PASS);
        if (activity.getLat() == 0 && activity.getLng() == 0) {
            UserPosition userPosition = corgiUserService.getUserPosition(getUserId());
            if (userPosition != null) {
                if (userPosition.getLng() != null && userPosition.getLng() < 200 && userPosition.getLat() != null && userPosition.getLat() < 200) {
                    activity.setLng(userPosition.getLng());
                    activity.setLat(userPosition.getLat());
                    activity.setCity(userPosition.getCity());
                } else if (userPosition.getRealLng() != null && userPosition.getRealLng() < 200 && userPosition.getRealLat() != null && userPosition.getRealLat() < 200) {
                    activity.setLng(userPosition.getRealLng());
                    activity.setLat(userPosition.getRealLat());
                    activity.setCity(userPosition.getCity());
                }
            }
        }
        activity = aliyunGreenService.checkImageActivity(activity);
        if (CorgiActivity.CAT_IMAGE.equals(activity.getCategory())) {
            List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getUserId(), CheckPic.ACTIVITY, "crazy_check");
            if (!checkActivityPic(activityPics)) {
                activity.setStrictStatus(AliyunGreenService.CHECK);
                activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getUserId(), CheckPic.ACTIVITY);
                if (!checkActivityPic(activityPics)) {
                    activity.setCheckStatus(AliyunGreenService.CHECK);
                }
            }
            activity.setPics(activityPics);
        }
        activity = corgiActivityService.addCorgiActivity(activity);
        corgiUserActivityService.updateActivityStatus(activity.getId(), activity.getCheckStatus());

        if (!StringUtils.isEmpty(activity.getId())) {
            String latestKey = "global_latest_activity" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            redisTemplate.opsForValue().setIfAbsent(latestKey, activity.getId(), 4L, TimeUnit.DAYS);
        }

        if (CorgiActivity.CAT_VIDEO.equals(activity.getCategory())) {
            CorgiVlog corgiVlog = new CorgiVlog();
            corgiVlog.setActivityId(activity.getId());
            corgiVlog.setUserId(activity.getUserId());
            corgiVlog.setVideoId(activity.getVideoId());
            corgiVlog.setType(CorgiVlog.TYPE.USER);
            corgiVlog.setStatus(CorgiVlog.STATUS.UNCHECK);
            corgiVlogService.addVlog(corgiVlog);
        }
        mqService.sendActivityPost(activity);
        if ("69548".equals(getUserId())) {
            CorgiVlogHot corgiVlogHot = new CorgiVlogHot();
            corgiVlogHot.setViewCount(null);
            corgiVlogHot.setLikeCount(0);
            corgiVlogHot.setActivityId(activity.getId());
            corgiVlogHot.setExpectView(3000);
            corgiVlogHot.setType(CorgiVlogHot.TYPE.MANUAL);
            corgiVlogService.addHotVlog(corgiVlogHot);
            //corgiActivityService.updateByColumn(corgiVlogHot.getActivityId(), "checkStatus", "good");
        }

        if (CollectionUtils.isEmpty(activity.getMentionUserIds())) {
            return new JsonResult(AddActivityResult.getResult(activity));
        }
        HashMap extra = new HashMap();
        extra.put("activityId", activity.getId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        UserDetail userDetail = corgiUserService.getUserDetailBasic(getUserId());
        for (String mentionUserId : activity.getMentionUserIds()) {
            if (!getUserId().equals(mentionUserId)) {
                mqService.sendMessage(PushMessage.builder()
                        .type(PushMessage.DEFAULT)
                        .sourceUserId(getUserId())
                        .targetUserId(mentionUserId)
                        .message(PushMessage.ACTIVITY_AT)
                        .extra(extra)
                        .build());
                corgiToolService.addActivityMessage(ActivityMessage.builder()
                        .activityId(activity.getId())
                        .fromUserAvatar(userDetail.getAvatar())
                        .fromUserName(userDetail.getNickname())
                        .fromUserId(getUserId())
                        .toUserId(mentionUserId)
                        .time(System.currentTimeMillis())
                        .content("@了你")
                        .messageType(ActivityMessage.COMMENT)
                        .build());
            }
        }
        return new JsonResult(AddActivityResult.getResult(activity));
    }

    @PostMapping("add_paying_activity")
    public JsonResult addPayingActivity(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        if (!redisTemplate.opsForValue().setIfAbsent("activity_sent_" + activity.getUserId(), System.currentTimeMillis() + "", 20L, TimeUnit.SECONDS)) {
            return new JsonResult(Constants.API_ERROR_CODE, "发送太频繁了哦");
        }
        if (redisTemplate.hasKey("darkroom_" + getUserId())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "禁止发布");
        }
        if (!this.checkRate(activity.getUserId(), CorgiActivity.CAT_PAYING, 10)) {
            return new JsonResult(Constants.API_ERROR_CODE, "一天只能发布10条动态");
        }
        String merchId = activity.getMerchId();
        CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(merchId, getUserId());
        if (merchandise == null) {
            return new JsonResult(Constants.API_ERROR_CODE, "价格不存在或者已失效，请重新选择");
        }
        activity.setAppMerchId(merchandise.getAppMerchId());
        if (StringUtils.isEmpty(activity.getCategory())) {
            activity.setCategory(CorgiActivity.CAT_PAYING);
        }
        if (!StringUtils.isEmpty(activity.getVideoId())) {
            GetMezzanineInfoResponse response = aliyunVodService.getVideoInfo(activity.getVideoId());
            GetMezzanineInfoResponse.Mezzanine mezzanine = response.getMezzanine();
            if (mezzanine != null) {
                activity.setHeight(mezzanine.getHeight());
                activity.setWidth(mezzanine.getWidth());
                activity.setVideoUrl(mezzanine.getFileURL().split("\\?Expires")[0]);
                GetVideoInfoResponse infoResponse = aliyunVodService.getVideoUrl(activity.getVideoId());
                if (infoResponse != null && infoResponse.getVideo() != null) {
                    if (StringUtils.isEmpty(activity.getCoverUrl())) {
                        activity.setCoverUrl(infoResponse.getVideo().getCoverURL().split("\\?Expires")[0]);
                    } else {
                        activity.setCoverUrl(activity.getCoverUrl().split("\\?Expires")[0]);
                    }
                    if ("Blocked".equals(infoResponse.getVideo().getAuditStatus())) {
                        activity.setCheckStatus(AliyunGreenService.FAIL);
                    }
                }
            }
        }
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity.setStrictStatus(AliyunGreenService.PASS);
        if (activity.getLat() == 0 && activity.getLng() == 0) {
            UserPosition userPosition = corgiUserService.getUserPosition(getUserId());
            if (userPosition != null) {
                if (userPosition.getLng() != null && userPosition.getLng() < 200 && userPosition.getLat() != null && userPosition.getLat() < 200) {
                    activity.setLng(userPosition.getLng());
                    activity.setLat(userPosition.getLat());
                    activity.setCity(userPosition.getCity());
                } else if (userPosition.getRealLng() != null && userPosition.getRealLng() < 200 && userPosition.getRealLat() != null && userPosition.getRealLat() < 200) {
                    activity.setLng(userPosition.getRealLng());
                    activity.setLat(userPosition.getRealLat());
                    activity.setCity(userPosition.getCity());
                }
            }
        }
        activity = aliyunGreenService.checkImageActivity(activity);
        if (!CollectionUtils.isEmpty(activity.getPics())) {
            List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getUserId(), CheckPic.ACTIVITY, "crazy_check");
            if (!checkActivityPic(activityPics)) {
                activity.setStrictStatus(AliyunGreenService.CHECK);
                activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getUserId(), CheckPic.ACTIVITY);
                if (!checkActivityPic(activityPics)) {
                    activity.setCheckStatus(AliyunGreenService.CHECK);
                }
            }
            activity.setPics(activityPics);
        }
        activity = corgiActivityService.addCorgiActivity(activity);
        corgiUserActivityService.updateActivityStatus(activity.getId(), activity.getCheckStatus());
        CorgiUserMarket market = CorgiUserMarket.builder()
                .userId(activity.getUserId())
                .sourceId(activity.getId())
                .goodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY)
                .merchId(merchId)
                .build();
        String marketId = corgiOrderService.addUserMarket(market);
        corgiActivityService.updateByColumn(activity.getId(), "marketId", marketId);
        if (!StringUtils.isEmpty(activity.getVideoId())) {
            CorgiVlog corgiVlog = new CorgiVlog();
            corgiVlog.setActivityId(activity.getId());
            corgiVlog.setUserId(activity.getUserId());
            corgiVlog.setVideoId(activity.getVideoId());
            corgiVlog.setType(CorgiVlog.TYPE.USER);
            corgiVlog.setStatus(CorgiVlog.STATUS.UNCHECK);
            corgiVlogService.addVlog(corgiVlog);
        }

        if (CollectionUtils.isEmpty(activity.getMentionUserIds())) {
            return new JsonResult(AddActivityResult.getResult(activity));
        }
        HashMap extra = new HashMap();
        extra.put("activityId", activity.getId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        UserDetail userDetail = corgiUserService.getUserDetailBasic(getUserId());
        for (String mentionUserId : activity.getMentionUserIds()) {
            if (!getUserId().equals(mentionUserId)) {
                mqService.sendMessage(PushMessage.builder()
                        .type(PushMessage.DEFAULT)
                        .sourceUserId(getUserId())
                        .targetUserId(mentionUserId)
                        .message(PushMessage.ACTIVITY_AT)
                        .extra(extra)
                        .build());
                corgiToolService.addActivityMessage(ActivityMessage.builder()
                        .activityId(activity.getId())
                        .fromUserAvatar(userDetail.getAvatar())
                        .fromUserName(userDetail.getNickname())
                        .fromUserId(getUserId())
                        .toUserId(mentionUserId)
                        .time(System.currentTimeMillis())
                        .content("@了你")
                        .messageType(ActivityMessage.COMMENT)
                        .build());
            }
        }
        return new JsonResult(AddActivityResult.getResult(activity));
    }

    @PostMapping("add_comment")
    public JsonResult addComment(@RequestBody ActivityComment activityComment) {
        String key = "comment_abandon_" + getUserId();
        if (redisTemplate.hasKey("darkroom_" + getUserId())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "禁止评论");
        }
        if (ActivityComment.SWIFT.equals(activityComment.getStatus())
                && !redisTemplate.opsForValue().setIfAbsent("quick_comment-" + activityComment.getActivityId() + "-" + getUserId(), "1", 7L, TimeUnit.DAYS)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "不要重复评论哦");
        }
        if (!StringUtils.isEmpty(redisTemplate.opsForValue().get(key))) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "评论得过快～ 休息一下去看看其他精彩内容吧。");
        }
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityComment.getActivityId()));
        if (CollectionUtils.isEmpty(activityList)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "评论失败，该动态已被删除");
        }
        if (!ActivityComment.SWIFT.equals(activityComment.getStatus())) {
            List<ActivityComment> comments = corgiCommentService.getActivityComment(activityList.get(0).getId(), null, null, "");
            if (!corgiUtilService.checkComment(comments, getUserId()) || !corgiUtilService.checkCommentFrequency(activityComment, getUserId())) {
                redisTemplate.opsForValue().set(key, System.currentTimeMillis() + "", 1l, TimeUnit.HOURS);
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "评论得过快～ 休息一下去看看其他精彩内容吧。");
            }
        }
        CheckTextResult textResult = aliyunGreenService.checkText(activityComment.getContent(), "ad_check");
        if (!ActivityComment.SWIFT.equals(activityComment.getStatus()) && !textResult.isPass()) {
            boolean noFilterContent = StringUtils.isEmpty(textResult.getContent());
            this.checkComment(getUserId());
            activityComment.setContent(noFilterContent ? activityComment.getContent().replaceAll(".", "*") : textResult.getContent());
        }
        Integer blackCount = corgiBlacklistService.isBlacked(activityList.get(0).getUserId(), getUserId());
        if (blackCount == 1) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "你已被拉黑");
        } else if (blackCount > 0) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "你已拉黑该用户");
        }
        activityComment.setUserId(activityList.get(0).getUserId());
        if (hasUserId()) {
            activityComment.setCommentUserId(getUserId());
        }
        if (StringUtils.isEmpty(activityComment.getStatus())) {
            activityComment.setStatus(ActivityComment.NORMAL);
        }
        if (ActivityComment.NORMAL.equals(activityComment.getStatus())
                && CorgiActivity.CAT_PAYING.equals(activityList.get(0).getCategory())
                && !CollectionUtils.isEmpty(corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                .userId(getUserId())
                .traderId(activityList.get(0).getUserId())
                .goodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY)
                .goodsId(activityComment.getActivityId())
                .start(0)
                .size(1)
                .build()))) {
            activityComment.setStatus(ActivityComment.PAY);
        }
        activityComment = corgiCommentService.addActivityComment(activityComment);
        HashMap extra = new HashMap();
        extra.put("activityId", activityComment.getActivityId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        if (!activityComment.getUserId().equals(activityComment.getCommentUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(activityComment.getCommentUserId())
                    .targetUserId(activityComment.getUserId())
                    .message(PushMessage.USER_COMMENT)
                    .extra(extra)
                    .build());
        }
        if (!StringUtils.isEmpty(activityComment.getReplyUserId())
                && !activityComment.getReplyUserId().equals(activityComment.getCommentUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(activityComment.getCommentUserId())
                    .targetUserId(activityComment.getReplyUserId())
                    .message(PushMessage.USER_COMMENT)
                    .extra(extra)
                    .build());
        }
        return new JsonResult(activityComment);
    }

    @PostMapping("like")
    public JsonResult like(@RequestBody ActivityLike activityLike) {
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityLike.getActivityId()));
        if (CollectionUtils.isEmpty(activityList) || activityList.get(0).getId() == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "点赞失败，该动态已被删除");
        }
        CorgiActivity activity = activityList.get(0);
        log.info("" + activity + activityLike);
        if (activity.getUserId() != null) {
            activityLike.setUserId(activity.getUserId());
        }
        if (hasUserId()) {
            activityLike.setLikeUserId(getUserId());
        }
        Integer result = corgiLikeService.addActivityLike(activityLike);
        if (result < 1) {
            return new JsonResult();
        }
        if (!StringUtils.isEmpty(activity.getVideoId())) {
            CorgiVlog corgiVlog = new CorgiVlog();
            corgiVlog.setActivityId(activityLike.getActivityId());
            corgiVlog.setLikeCount(1);
            corgiVlogService.addVlogCount(corgiVlog);
        }
        HashMap extra = new HashMap();
        extra.put("activityId", activityLike.getActivityId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        if (!activityLike.getLikeUserId().equals(activityLike.getUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(activityLike.getLikeUserId())
                    .targetUserId(activityLike.getUserId())
                    .message(PushMessage.USER_LIKE)
                    .extra(extra)
                    .build());
            mqService.sendLikePost(activityLike);
        }
        return new JsonResult();
    }

    @GetMapping("unlike")
    public JsonResult unlike(@RequestParam("activityId") String activityId) {
        Integer result = corgiLikeService.deleteActivityLike(getUserId(), activityId);
        if (result > 0) {
            CorgiVlog corgiVlog = new CorgiVlog();
            corgiVlog.setActivityId(activityId);
            corgiVlog.setLikeCount(-1);
            corgiVlogService.addVlogCount(corgiVlog);
        }
        return new JsonResult();
    }

    @GetMapping("delete_comment")
    public JsonResult deleteComment(@RequestParam("commentId") String commentId) {
        corgiCommentService.deleteActivityComment(commentId);
        return new JsonResult();
    }

    @GetMapping("like_comment")
    public JsonResult likeComment(@RequestParam("commentId") String commentId) {
        ActivityComment activityComment = corgiCommentService.likeComment(commentId, getUserId());
        HashMap extra = new HashMap();
        extra.put("activityId", activityComment.getActivityId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        if (!activityComment.getCommentUserId().equals(getUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(getUserId())
                    .targetUserId(activityComment.getCommentUserId())
                    .message(PushMessage.COMMENT_LIKE)
                    .extra(extra)
                    .build());
        }
        return new JsonResult();
    }

    @GetMapping("unlike_comment")
    public JsonResult unlikeComment(@RequestParam("commentId") String commentId) {
        corgiCommentService.disLikeComment(commentId, getUserId());
        return new JsonResult();
    }

    @GetMapping("get_comment_by_id")
    public JsonResult getCommentById(@RequestParam("commentId") String commentId) {
        return new JsonResult(corgiCommentService.getCommentByCommentId(commentId));
    }

    @GetMapping("get_comments")
    public JsonResult getComment(@RequestParam(value = "activityId", required = false) String activityId,
                                 @RequestParam(required = false, name = "lastId") Integer id,
                                 @RequestParam(required = false, name = "size") Integer size) {
        if (StringUtils.isEmpty(activityId)) {
            return new JsonResult<>(new ArrayList<>());
        }
        if ("AppStore".equals(RequestUtil.getChannel())) {
            List<ActivityComment> hotComments = corgiCommentService.getHotComment(activityId, getUserId());
            List<ActivityComment> activityComments = corgiCommentService.getActivityComment(activityId, id, size, getUserId());
            activityComments.addAll(0, hotComments);
            return new JsonResult(activityComments);
        } else {
            return new JsonResult(corgiCommentService.getUserActivityComment(activityId, getUserId()));
        }
    }

    @GetMapping("get_hot_comments")
    public JsonResult getHotComment(@RequestParam("activityId") String activityId) {
        if ("AppStore".equals(RequestUtil.getChannel())) {
            List<ActivityComment> activityComments = corgiCommentService.getHotComment(activityId, getUserId());
            return new JsonResult(activityComments);
        } else {
            return new JsonResult(new ArrayList<>());
        }
    }

    @GetMapping("get_likes")
    public JsonResult getLike(@RequestParam("activityId") String activityId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<ActivityLike> activityLikes = corgiLikeService.getActivityLike(activityId, page, pageSize);
        for (ActivityLike like : activityLikes) {
            like.setIsFollow(corgiUserFollowService.isFollowed(getUserId(), like.getLikeUserId()));
            like.setType(null);
        }
        return new JsonResult(activityLikes);
    }

    @GetMapping("count_comment")
    public JsonResult countComment(@RequestParam("activityId") String activityId) {
        if ("AppStore".equals(RequestUtil.getChannel())) {
            Long count = corgiCommentService.countActivityComment(activityId);
            return new JsonResult(count);
        } else {
            return new JsonResult();
        }
    }

    @GetMapping("count_like")
    public JsonResult countLike(@RequestParam("activityId") String activityId) {
        Long count = corgiLikeService.countActivityLike(activityId);
        List<ActivityLike> users = corgiLikeService.getFollowUser(getUserId(), activityId);
        LikeCount likeCount = new LikeCount();
        likeCount.setCount(count);
        likeCount.setUsers(users);
        return new JsonResult(likeCount);
    }

    @GetMapping("get_activity_message")
    public JsonResult getActivityMessage(@RequestParam("pageSize") Integer pageSize, @RequestParam(required = false, name = "type") String type) {
        List<ActivityMessage> activityMessages;
        if (StringUtils.isEmpty(type)) {
            activityMessages = corgiToolService.getActivityMessage(getUserId(), pageSize);
        } else {
            activityMessages = corgiToolService.getActivityMessageByType(getUserId(), pageSize, type);
        }
        return new JsonResult(activityMessages);
    }

    @GetMapping("get_all_activity_message")
    public JsonResult getAllActivityMessage(@RequestParam("page") Integer page
            , @RequestParam("pageSize") Integer pageSize
            , @RequestParam(required = false, name = "type") String type) {
        List<ActivityMessage> activityMessages;
        if (StringUtils.isEmpty(type)) {
            activityMessages = corgiToolService.getAllActivityMessage(getUserId(), page, pageSize);
        } else {
            activityMessages = corgiToolService.getAllActivityMessageByType(getUserId(), page, pageSize, type);
        }
        return new JsonResult(activityMessages);
    }

    @GetMapping("get_is_like")
    public JsonResult getIsLike(@RequestParam("activityId") String activityId) {
        Integer isLike = corgiLikeService.countUserLike(activityId, getUserId());
        return new JsonResult(isLike);
    }


    @GetMapping("count_activity_message")
    public JsonResult countActivityMessage(@RequestParam(required = false, name = "type") String type) {
        ActivityMessageCount messageCount = new ActivityMessageCount();
        Long count = 0L;
        if (StringUtils.isEmpty(type)) {
            count = corgiToolService.countActivityMessage(getUserId());
        } else {
            count = corgiToolService.countActivityMessageByType(getUserId(), type);
        }
        messageCount.setCount(count);
        if (count != null && count > 0) {
            ActivityMessage activityMessage;
            if (StringUtils.isEmpty(type)) {
                activityMessage = corgiToolService.getLastActivityMessage(getUserId());
            } else {
                activityMessage = corgiToolService.getLastActivityMessageByType(getUserId(), type);
            }
            if (activityMessage != null && activityMessage.getFromUserAvatar() != null) {
                messageCount.setPicUrl(activityMessage.getFromUserAvatar());
            }
        }
        return new JsonResult(messageCount);
    }

    @GetMapping("delete_activity_message")
    public JsonResult deleteActivityMessage(@RequestParam("time") Long time) {
        corgiToolService.deleteActivityMessage(getUserId(), time);
        return new JsonResult();
    }

    @GetMapping("test_add_activity")
    public JsonResult testAddActivity() {
        CorgiActivity query = new CorgiActivity();
        query.setCategory(CorgiActivity.CAT_BUSINESS);
        query.setStatus(CorgiActivity.NOT_DELETED);
        List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(query, 1, 1000);
        for (CorgiActivity business : activityList) {
            log.info("business .. {} ", business);
            if (StringUtils.isEmpty(business.getCity())) {
                BarProfile barProfile = corgiBarService.getBarProfile(business.getUserId());
                log.info(" bar ... {} ", barProfile);
                if (barProfile != null && !StringUtils.isEmpty(barProfile.getCity())) {
                    business.setCity(barProfile.getCity());
                    corgiActivityService.updateCorgiActivity(business);
                }
            }
        }
        return new JsonResult();
    }

    @PostMapping("update_activity")
    public JsonResult updateActivity(@RequestBody CorgiActivity activity) throws PermissionException {
        if (hasUserId()) {
            if (!checkActivityUser(activity.getId(), getUserId())) {
                throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
            }
            activity.setUserId(getUserId());
        }
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activity.getId()));
        if (!CollectionUtils.isEmpty(activityList)) {
            activity.setCategory(activityList.get(0).getCategory());
        }
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkActivity(activity);
        activity = corgiActivityService.updateCorgiActivity(activity);
        corgiUserActivityService.deleteSignUpByActivity(activity.getId());
        if (CorgiActivity.FULL.equals(activity.getStatus())) {
            activity.setStatus(CorgiActivity.CREATED);
            corgiActivityService.updateCorgiActivityStatus(activity);
        }
        if (CorgiActivity.CAT_ACTIVITY.equals(activity.getCategory())) {
            List<ActivityPic> activitypics = corgiPicService.getActivityPic(activity.getId());
//            List<UserProfile> recommendUser = corgiUserService.recommendUser(activity.getCity(), activity.getUserId());
//            if (CollectionUtils.isEmpty(recommendUser)) {
//                recommendUser = corgiUserFollowService.getMatchUserByPage(activity.getUserId(), "active", 0.0, 0.0, 1, 6);
//            }
            return new JsonResult(AddActivityResult.getResult(activity)
//                    .setRecommend(recommendUser)
                    .setActivityPics(activitypics)
                    .setCanCallCity(getCallCityKey(activity.getUserId()) != null)
                    .setHasCallCity(redisTemplate.hasKey(CALL_CITY_PREFIX.concat(activity.getId()))));
        } else {
            return new JsonResult(activity);
        }
    }

    @PostMapping("update_activity_uncheck")
    public JsonResult updateActivityUncheck(@RequestBody CorgiActivity activity) throws PermissionException {
        if (hasUserId()) {
            log.info("into update_activity..." + getUserId());
            if (!checkActivityUser(activity.getId(), getUserId())) {
                throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
            }
        }
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkActivity(activity);
        activity = corgiActivityService.updateCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @GetMapping("delete_activity")
    public JsonResult deleteActivity(@RequestParam("activityId") String activityId) throws PermissionException {
        if (hasUserId()) {
            log.info("into delete_activity..." + getUserId());
            if (!checkActivityUser(activityId, getUserId())) {
                throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
            }
        }
        corgiUserActivityService.deleteActivityCreator(activityId);
        corgiVlogService.deleteVlog(activityId);
        corgiActivityService.deleteCorgiActivity(activityId);
        corgiLikeService.deleteActivityLike(null, activityId);
        return new JsonResult();
    }

    @GetMapping("sign_up")
    public JsonResult signUp(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiUserActivityService.signUp(new UserSignUp(userId, activityId));
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
        if (CollectionUtils.isEmpty(corgiActivities)) {
            return new JsonResult(Constants.API_ERROR_CODE, "该动态已被删除");
        }
        UserDetail userDetail = corgiUserService.getUserDetail(userId, null);
        HashMap extra = new HashMap();
        extra.put("activityId", activityId);
        extra.put("type", 902);
        populateExtra(extra, activityId, null);
        mqService.sendMessage(PushMessage.builder()
                .sourceUserId(userId)
                .type(PushMessage.ACTIVITY + "_city")
                .targetUserId(corgiActivities.get(0).getUserId())
                .extra(extra)
                .message(userDetail.getNickname().concat("刚刚报名了一个本地活动！"))
                .build());

        return new JsonResult();
    }

    @GetMapping("sign_out")
    public JsonResult signOut(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiUserActivityService.signOut(new UserSignUp(userId, activityId));
        return new JsonResult();
    }

    @GetMapping("invite")
    public JsonResult invite(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        String lockKey = "agree_" + activityId;
        corgiUtilService.lock(lockKey);
        try {
            List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
            if (CollectionUtils.isEmpty(activityList)) {
                return new JsonResult(Constants.API_ERROR_CODE, "该动态已被删除");
            }
            CorgiActivity activity = activityList.get(0);
            if (activity.getStatus().equals(CorgiActivity.DELETED)) {
                return new JsonResult(Constants.API_ERROR_CODE, "活动已被删除");
            }
            int peopleCount = activity.getPeopleCount() - 1;
            boolean hasUser = false;
            List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activityId, userId, "");
            int count = 0;
            int userStatus = 0;
            for (UserProfile userProfile : userProfiles) {
                if (userProfile.getSignUpStatus() == UserSignUp.AGREE) {
                    count++;
                }
                hasUser |= userProfile.getUserId().equals(userId);
                if (userProfile.getUserId().equals(userId)) {
                    userStatus = userProfile.getSignUpStatus();
                }
            }
            if (userStatus == UserSignUp.AGREE) {
                return new JsonResult();
            }
            if (activity.getStatus().equals(CorgiActivity.ENDED)) {
                return new JsonResult(Constants.API_ERROR_CODE, "报名已结束");
            }
            if (activity.getStatus().equals(CorgiActivity.FULL)) {
                return new JsonResult(Constants.API_ERROR_CODE, "抱歉，该活动已满员");
            }
            if (count >= peopleCount) {
                activity.setStatus(CorgiActivity.FULL);
                corgiActivityService.updateCorgiActivityStatus(activity);
                return new JsonResult(Constants.API_ERROR_CODE, "抱歉，该活动已满员");
            }
            if (!hasUser) {
                UserSignUp userSignUp = new UserSignUp(userId, activityId);
                userSignUp.setStatus(UserSignUp.AGREE);
                corgiUserActivityService.signUp(userSignUp);
            } else {
                UserSignUp userSignUp = new UserSignUp(userId, activityId);
                userSignUp.setStatus(UserSignUp.AGREE);
                corgiUserActivityService.updateSignUp(userSignUp);
            }
            count++;
            if (peopleCount == (count)) {
                activity.setStatus(CorgiActivity.FULL);
                corgiActivityService.updateCorgiActivityStatus(activity);
            }
            HashMap extra = new HashMap();
            extra.put("activityId", activityId);
            extra.put("type", PushMessage.AGREE_MESSAGE_TYPE);
            mqService.sendMessage(PushMessage.builder()
                    .targetUserId(userId)
                    .message(PushMessage.AGREE_MESSAGE)
                    .extra(extra)
                    .build());
            return new JsonResult();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new JsonResult(Constants.API_ERROR_CODE);
        } finally {
            corgiUtilService.unlock(lockKey);
        }
    }

    @GetMapping("agree")
    public JsonResult agree(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) throws PermissionException {
        if (hasUserId()) {
            log.info("into agree..." + getUserId());
            if (!checkActivityUser(activityId, getUserId())) {
                throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
            }
        }
        String lockKey = "agree_" + activityId;
        corgiUtilService.lock(lockKey);
        try {
            List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityId));

            if (CollectionUtils.isEmpty(activityList)) {
                return new JsonResult(Constants.API_ERROR_CODE, "该动态已被删除");
            }
            CorgiActivity activity = activityList.get(0);
            if (activity.getStatus().equals(CorgiActivity.DELETED)) {
                return new JsonResult(Constants.API_ERROR_CODE, "活动已被删除");
            }
            if (activity.getStatus().equals(CorgiActivity.ENDED)) {
                return new JsonResult(Constants.API_ERROR_CODE, "报名已结束");
            }
            if (activity.getStatus().equals(CorgiActivity.FULL)) {
                return new JsonResult(Constants.API_ERROR_CODE, "抱歉，该活动已满员");
            }
            int peopleCount = activity.getPeopleCount() - 1;
            boolean hasUser = false;
            List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activityId, null, "");
            int count = 0;
            for (UserProfile userProfile : userProfiles) {
                if (userProfile.getSignUpStatus() == UserSignUp.AGREE) {
                    count++;
                } else {
                    hasUser |= userProfile.getUserId().equals(userId);
                }
            }
            if (count >= peopleCount) {
                activity.setStatus(CorgiActivity.FULL);
                corgiActivityService.updateCorgiActivityStatus(activity);
                return new JsonResult(Constants.API_ERROR_CODE, "抱歉，该活动已满员");
            }
            if (hasUser) {
                UserSignUp userSignUp = new UserSignUp(userId, activityId);
                userSignUp.setStatus(UserSignUp.AGREE);
                corgiUserActivityService.updateSignUp(userSignUp);
                count++;
                if (peopleCount == count) {
                    activity.setStatus(CorgiActivity.FULL);
                    corgiActivityService.updateCorgiActivityStatus(activity);
                }
                HashMap extra = new HashMap();
                extra.put("activityId", activityId);
                extra.put("type", PushMessage.AGREE_MESSAGE_TYPE);
                mqService.sendMessage(PushMessage.builder()
                        .targetUserId(userId)
                        .message(PushMessage.AGREE_MESSAGE)
                        .extra(extra)
                        .build());
            }
            return new JsonResult();
        } finally {
            corgiUtilService.unlock(lockKey);
        }
    }

    @GetMapping("get_detail")
    public JsonResult getDetail(@RequestParam("activityId") String activityId, @RequestParam("userId") String userId) {
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
        if (CollectionUtils.isEmpty(corgiActivities) || corgiActivities.get(0) == null) {
            return new JsonResult(Constants.API_ERROR_CODE, "该动态已被删除");
        }
        if (hasUserId()) {
            userId = getUserId();
        }
        CorgiActivity activity = corgiActivities.get(0);
        List<CorgiActivity> activities = new ArrayList<>();
        activities.add(corgiActivities.get(0));
        List<CorgiActivityDetail> details = convertDetail(activities, userId, true);
        if (CollectionUtils.isEmpty(details)) {
            return new JsonResult(Constants.API_ERROR_CODE, "该动态已被删除");
        }
        CorgiActivityDetail detail = details.get(0);
        Integer blackCount = corgiBlacklistService.isBlacked(detail.getUserId(), userId);
        if (blackCount > 0) {
            detail = new CorgiActivityDetail();
            if (blackCount == 1) {
                detail.setCheckStatus("blocked");
            } else if (blackCount == 2) {
                detail.setCheckStatus("block");
            } else {
                detail.setCheckStatus("mutual");
            }
        }
        detail.setCanCallCity("69548".equals(getUserId()) || (activity.getUserId().equals(getUserId()) && !StringUtils.isEmpty(getCallCityKey(getUserId()))));
        detail.setHasCallCity(redisTemplate.hasKey(CALL_CITY_PREFIX.concat(activityId)));
        return new JsonResult(detail);
    }

    @GetMapping("count_follow_activity")
    public JsonResult countFollowActivity() {
        String key = "latest_activity_" + getUserId();
        String activityId = redisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(activityId)) {
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DATE, -3);
            key = "global_latest_activity" + new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime());
            activityId = redisTemplate.opsForValue().get(key);
        }
        if (StringUtils.isEmpty(activityId)) {
            return new JsonResult(0);
        }
        ActivityQuery query = new ActivityQuery();
        query.setActivityId(activityId);
        query.setUserId(getUserId());
        return new JsonResult(corgiUserActivityService.countFollowUserActivity(query));
    }

    @GetMapping("get_follow_paying")
    public JsonResult getFollowPaying() {
        ActivityQuery query = new ActivityQuery();
        query.setUserId(getUserId());
        query.setPage(0);
        query.setPageSize(1000);
        query.setCategory(CorgiActivity.CAT_PAYING);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);
        query.setEndTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(calendar.getTime()));
        List<String> activityIds = corgiUserActivityService.getFollowUserActivity(query);
        return new JsonResult(convertFollowActivity(activityIds, getUserId()));
    }

    @GetMapping("get_follow_activity")
    public JsonResult getFollowActivity(ActivityQuery activityQuery) {
        if (hasUserId()) {
            activityQuery.setUserId(getUserId());
        }
        String userId = activityQuery.getUserId();
        String key = "latest_activity_" + userId;
        String latestActivityId = redisTemplate.opsForValue().get(key);
        Integer page = activityQuery.getPage();
        if (page != null) {
            activityQuery.setPage((page - 1) * activityQuery.getPageSize());
        } else {
            activityQuery.setPage(0);
        }
        List<String> activityIds = corgiUserActivityService.getFollowUserActivity(activityQuery);
        if (CollectionUtils.isEmpty(activityIds)) {
            return new JsonResult();
        }
        if (StringUtils.isEmpty(latestActivityId)) {
            latestActivityId = activityIds.get(0);
        } else if (latestActivityId.compareTo(activityIds.get(0)) < 0) {
            latestActivityId = activityIds.get(0);
        }
        redisTemplate.opsForValue().set(key, latestActivityId, 3L, TimeUnit.DAYS);
        return new JsonResult(convertDetail(corgiActivityService.getActivityByIds(activityIds), activityQuery.getUserId(), true));
    }

    @GetMapping("refuse")
    public JsonResult refuse(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) throws PermissionException {
        if (hasUserId()) {
            log.info("into refuse..." + getUserId());
            if (!checkActivityUser(activityId, getUserId())) {
                throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
            }
        }
        String lockKey = "agree_" + activityId;
        corgiUtilService.lock(lockKey);
        try {
            List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityId));

            if (CollectionUtils.isEmpty(activityList)) {
                return new JsonResult(Constants.API_ERROR_CODE, "该动态已被删除");
            }
            CorgiActivity activity = activityList.get(0);
            if (activity.getStatus().equals(CorgiActivity.DELETED)) {
                return new JsonResult(Constants.API_ERROR_CODE, "活动已被删除");
            }
            if (activity.getStatus().equals(CorgiActivity.ENDED)) {
                return new JsonResult(Constants.API_ERROR_CODE, "报名已结束");
            }
            int peopleCount = activity.getPeopleCount() - 1;
            UserSignUp userSignUp = new UserSignUp(userId, activityId);
            userSignUp.setStatus(UserSignUp.REFUSE);
            corgiUserActivityService.updateSignUp(userSignUp);
            List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activityId, userId, UserSignUp.AGREE + "");
            int count = 0;
            for (UserProfile userProfile : userProfiles) {
                if (userProfile.getSignUpStatus() == UserSignUp.AGREE) {
                    count++;
                }
            }
            if (count >= peopleCount) {
                activity.setStatus(CorgiActivity.FULL);
            } else {
                activity.setStatus(CorgiActivity.CREATED);
            }
            corgiActivityService.updateCorgiActivityStatus(activity);
            return new JsonResult();
        } finally {
            corgiUtilService.unlock(lockKey);
        }
    }

    @GetMapping("get_sign_up_users")
    public JsonResult getSignUpUsers(@RequestParam("activityId") String activityId, @RequestParam("userId") String userId, @RequestParam(name = "status", required = false) String status) {
        List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activityId, userId, status);
        return new JsonResult(userProfiles);
    }


    @GetMapping("get_sign_up_activity")
    public JsonResult getSignUpActivity(@RequestParam("userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<String> activityIds = corgiUserActivityService.getSignUpActivity(userId, page, pageSize);
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivityDetail> detailList = convertDetail(activityList, userId);
        return new JsonResult(detailList);
    }

    @GetMapping("search_activity")
    public JsonResult searchActivity(CorgiActivity activity, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (CorgiActivity.CAT_ACTIVITY.equals(activity.getCategory())) {
            activity.setStatus(CorgiActivity.CREATED);
        } else {
            activity.setStatus(CorgiActivity.NOT_DELETED);
        }
        activity.setStrictStatus(RequestUtil.getChannel());
        List<CorgiActivity> activityList = corgiActivityService.searchActivity(activity, page, pageSize);
        List<CorgiActivityDetail> detailList = convertDetail(activityList, getUserId());
        return new JsonResult(detailList);
    }

    @GetMapping("query_hot_pay_activity")
    public JsonResult getHotPayActivity(@RequestParam("page") Integer page,
                                        @RequestParam("pageSize") Integer pageSize) {
        String timeKey = "query_hot_activity_" + getUserId();
        String ctime = redisTemplate.opsForValue().get(timeKey);
        if (page == 1) {
            ctime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            redisTemplate.opsForValue().set(timeKey, ctime, 12l, TimeUnit.HOURS);
        }
        CorgiUserGoods query = new CorgiUserGoods();
        query.setGoodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY);
        query.setStart((page - 1) * pageSize);
        query.setSize(pageSize);
        query.setCtime(ctime);
        List<CorgiUserGoods> goods = corgiOrderService.getHotGoods(query);
        if (CollectionUtils.isEmpty(goods)) {
            return new JsonResult(new ArrayList<>());
        }
        return new JsonResult(convertDetail(
                corgiActivityService.getActivityByIds(goods.stream()
                        .map(CorgiUserGoods::getGoodsId).collect(Collectors.toList())),
                getUserId()));
    }


    @GetMapping("get_pay_activity")
    public JsonResult getPayActivity(@RequestParam("page") Integer page,
                                     @RequestParam("pageSize") Integer pageSize) {
        CorgiUserGoods query = new CorgiUserGoods();
        query.setUserId(getUserId());
        query.setGoodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY);
        query.setStart((page - 1) * pageSize);
        query.setSize(pageSize);
        List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(query);
        if (CollectionUtils.isEmpty(goods)) {
            return new JsonResult(new ArrayList<>());
        }
        return new JsonResult(convertDetail(
                corgiActivityService.getActivityByIds(goods.stream()
                        .map(CorgiUserGoods::getGoodsId).collect(Collectors.toList())),
                getUserId()));
    }

    @GetMapping("get_activity_by_hashtag")
    public PageResult getActivityByHashtag(@RequestParam("userId") String userId,
                                           @RequestParam("hashtagId") String hashtagId,
                                           @RequestParam("page") Integer page,
                                           @RequestParam("pageSize") Integer pageSize) {
        List<String> activityIds = corgiToolService.getActivityIdsByHashtag(hashtagId, page, pageSize);
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivityDetail> detailList = convertDetail(activities, userId);
        return new PageResult(detailList, page + 1, 0);
    }

    @PostMapping("get_range_image")
    public PageResult postRangeImage(@RequestBody ActivityQuery activityQuery) {
        return getRangeImage(null, 0, 0, activityQuery);
    }

    @GetMapping("get_range_image")
    public PageResult getRangeImage(@RequestParam("userId") String userId, @RequestParam(name = "lng", required = false, defaultValue = "0") double lng, @RequestParam(name = "lat", required = false, defaultValue = "0") double lat, ActivityQuery activityQuery) {
        if (activityQuery.getTPage() == null || activityQuery.getTPage() < 1) {
            activityQuery.setTPage(1);
        }
        List<String> activityIds;
        List<CorgiActivity> activityList;
        if (corgiUtilService.isNewUser(getUserId())) {
            activityIds = corgiFeedService.getPopularFeed(userId, activityQuery.getPageSize());
            activityList = corgiActivityService.getActivityByIds(activityIds);
        } else if (!StringUtils.isEmpty(activityQuery.getCity())) {
            activityQuery.setUserId("");
            activityQuery.setVersion(RequestUtil.getChannel());
            activityList = corgiActivityService.getFeedActivity(activityQuery);
        } else {
            activityIds = corgiToolService.getActivityIdsByTopic(activityQuery, activityQuery.getTPage(), activityQuery.getPageSize());
            activityList = corgiActivityService.getActivityByIds(activityIds);
        }
        //List<CorgiActivity> activities =
        List<CorgiActivityDetail> detailList = convertDetail(activityList, getUserId());
        return new PageResult(detailList, 1, activityQuery.getDPage());
    }

    @GetMapping("get_by_topic")
    public JsonResult getByTopic(ActivityQuery activityQuery) {
        List<String> activityIds = null;
        String topic = activityQuery.getTopic();
        if (corgiUtilService.isNewUser(getUserId())) {
            activityIds = corgiFeedService.getPopularFeed(getUserId(), activityQuery.getPageSize());
        } else {
            CorgiActivity query = new CorgiActivity();
            query.setCity(activityQuery.getCity() == null ? "" : activityQuery.getCity());
            query.setTopics(Arrays.asList(topic));
            int page = activityQuery.getPage();
            int size = activityQuery.getPageSize();
            if (page * size > 21) {
                size = 21 - (page - 1) * size;
            }
            if (size <= 0 || size > 21) {
                activityIds = new ArrayList<>();
            } else {
                activityIds = corgiUserActivityService.getHeatActivity(query, page, size);
            }
        }
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivity> result = new ArrayList<>();
        if (activities != null) {
            for (CorgiActivity activity : activities) {
                if (!"AppStore".equals(RequestUtil.getChannel()) && ("check".equals(activity.getStrictStatus()) || CorgiActivity.CAT_VIDEO.equals(activity.getCategory()))) {
                    continue;
                }
                if (!AliyunGreenService.NOT_GOOD.equals(activity.getCheckStatus())) {
                    result.add(activity);
                }
            }
        }
        CorgiTopic topicDetail = corgiToolService.getTopic(topic);
        return new JsonResult(new CorgiTopicList(topicDetail, result));
    }

    @GetMapping("get_by_hashtag")
    public JsonResult getByTopic(
            @RequestParam("hashtagId") String hashtagId,
            @RequestParam("page") Integer page,
            @RequestParam("pageSize") Integer pageSize) {
        CorgiActivity query = new CorgiActivity();
        query.setHashtags(Arrays.asList(hashtagId));
        List<String> activityIds = corgiUserActivityService.getHeatActivity(query, page, pageSize);
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivity> result = new ArrayList<>();
        if (activities != null) {
            for (CorgiActivity activity : activities) {
                if (!AliyunGreenService.NOT_GOOD.equals(activity.getCheckStatus())) {
                    result.add(activity);
                }
            }
        }
        CorgiHashtag corgiHashtag = corgiToolService.getHashtag(hashtagId);
        Hashtag hashtag = new Hashtag();
        BeanUtils.copyProperties(corgiHashtag, hashtag);
        CorgiVlog countResult = corgiVlogService.countByHashtag(hashtagId, null);
        hashtag.initCount(countResult);
        return new JsonResult(new CorgiHashtagList(hashtag, result));
    }

    @PostMapping("get_city_activity")
    public JsonResult getCityActivity(@RequestBody ActivityQuery activityQuery) {
        return getRangeActivity(activityQuery);
    }

    @GetMapping("get_city_activity")
    public JsonResult getRangeActivity(ActivityQuery activityQuery) {
        String userId = getUserId();
        activityQuery.setUserId(null);
        activityQuery.setLoginUserId(userId);
        List<CorgiActivity> activityList;
        if (corgiUtilService.isNewUser(getUserId())) {
            List<String> activityIds = corgiFeedService.getPopularFeed(getUserId(), activityQuery.getPageSize());
            activityList = corgiActivityService.getActivityByIds(activityIds);
        } else if (activityQuery.checkUser()) {
            List<String> activityIds = corgiUserActivityService.searchFeedActivity(activityQuery);
            activityList = corgiActivityService.getActivityByIds(activityIds);
        } else {
            activityList = corgiActivityService.getFeedActivity(activityQuery);
        }
        if (!CollectionUtils.isEmpty(activityList) && activityList.size() >= 5) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            CorgiVlog query = new CorgiVlog();
            query.setUserId(getUserId());
            query.setCtime(sdf.format(new Date()));
            Integer size = activityList.size() / 5;
            List<CorgiVlog> corgiVlogs = corgiVlogService.recallBarVlog(query, size);
            List<String> activityIds = new ArrayList<>();
            for (CorgiVlog vlog : corgiVlogs) {
                activityIds.add(vlog.getActivityId());
                CorgiFeed feed = new CorgiFeed();
                feed.setFeed(vlog.getActivityId());
                feed.setUserId(getUserId());
                feed.setFeedUserId(vlog.getUserId());
                corgiFeedService.addBarFeed(feed);
            }
            List<CorgiActivity> businessList = corgiActivityService.getActivityByIds(activityIds);
            activityList = mergeActivity(activityList, businessList);
        }
        List<CorgiActivityDetail> detailList = convertDetail(activityList, userId);
        return new JsonResult(detailList);
    }

    @GetMapping("get_range_activity")
    public JsonResult getRangeActivity(@RequestParam("userId") String userId,
                                       @RequestParam(name = "lng", required = false, defaultValue = "0") double lng,
                                       @RequestParam(name = "lat", required = false, defaultValue = "0") double lat,
                                       @RequestParam(name = "range", required = false, defaultValue = "0") double range, ActivityQuery activityQuery) {
        if (activityQuery.getPage() == null) {
            activityQuery.setPage(1);
        }
        activityQuery.setSort(ActivityQuery.SORT_TIME);
        if (CorgiActivity.CAT_BUSINESS.equals(activityQuery.getCategory())) {
            activityQuery.setCity("");
        }
        List<CorgiActivity> businessList = corgiActivityService.getCorgiActivityByRange(lng, lat, range, activityQuery);
        List<CorgiActivityDetail> detailList = convertDetail(businessList, userId);
        return new JsonResult(detailList);
    }

    @GetMapping("get_user_activity")
    public JsonResult getMyRunningActivity(ActivityQuery query) {
        query.setLoginUserId(getUserId());
        if (!getUserId().equals(query.getUserId()) && corgiUtilService.isNewUser(getUserId())) {
            return new JsonResult(new ArrayList());
//            List<String> activityIds = corgiFeedService.getPopularFeed(getUserId(), query.getPageSize());
//            return new JsonResult(convertDetail(corgiActivityService.getActivityByIds(activityIds), getUserId(), false));
        } else {
            return new JsonResult(convertDetail(corgiActivityService.getFeedActivity(query), getUserId(), !CorgiUtilService.CHANNELS.contains(RequestUtil.getChannel())));
        }
    }

    @GetMapping("get_user_video")
    public JsonResult getUserVideo(ActivityQuery query) {
        query.setCategory(CorgiActivity.CAT_VIDEO);
        query.setLoginUserId(getUserId());
        return new JsonResult(convertDetail(corgiActivityService.getFeedActivity(query), getUserId()));
    }

    @GetMapping("get_user_running_activity")
    public JsonResult getUserRunningActivity(@RequestParam("userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiActivity> result = corgiActivityService.getUserRunningActivity(userId, page, pageSize);
        return new JsonResult(result);
    }

    @GetMapping("get_participate_activity")
    public JsonResult getUserParticipateActivity(@RequestParam(required = false, name = "userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        List<String> activityIds = corgiUserActivityService.getParticipateActivity(userId, page, pageSize);
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivity> result = new ArrayList<>();
        for (CorgiActivity activity : activityList) {
            if (!CorgiActivity.DELETED.equals(activity.getStatus())) {
                activity.setPics(corgiPicService.getActivityPic(activity.getId()));
                result.add(activity);
            }
        }
        return new JsonResult(result);
    }


    @GetMapping("/delete_activity_pic")
    public JsonResult deleteUserPic(@RequestParam("picId") String picId) {
        String result = corgiPicService.deleteActivityPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/add_activity_pic")
    public JsonResult addUserPic(@RequestBody List<ActivityPic> activityPics) {
        if (CollectionUtils.isEmpty(activityPics) || activityPics.get(0) == null) {
            return new JsonResult();
        }
        String activityId = activityPics.get(0).getActivityId();
        List<ActivityPic> activityPicList = (List<ActivityPic>) aliyunGreenService.checkPic(activityPics, getUserId(), CheckPic.ACTIVITY);
        String status = AliyunGreenService.PASS;
        for (ActivityPic pic : activityPicList) {
            String result = corgiPicService.addActivityPic(pic);
            pic.setPicId(result);
            if (AliyunGreenService.CHECK.equals(pic.getStatus())) {
                status = pic.getStatus();
            }
        }
        if (AliyunGreenService.CHECK.equals(status)) {
            CorgiActivity updateActivity = new CorgiActivity();
            updateActivity.setId(activityId);
            updateActivity.setCheckStatus(status);
            corgiActivityService.updateCorgiActivityStatus(updateActivity);
        }
        return new JsonResult(activityPicList);
    }

    @GetMapping("add_favor")
    public JsonResult addFavor(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiFavorActivityService.addFavor(userId, activityId);
        return new JsonResult();
    }

    @GetMapping("delete_favor")
    public JsonResult deleteFavor(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiFavorActivityService.deleteFavor(userId, activityId);
        return new JsonResult();
    }

    @GetMapping("check_favor")
    public JsonResult checkFavor(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        int result = corgiFavorActivityService.countActivity(userId, activityId);
        return new JsonResult(result);
    }

    @GetMapping("get_favor")
    public JsonResult getFavor(@RequestParam("userId") String userId, @RequestParam(name = "page", required = false, defaultValue = "1") Integer page, @RequestParam(name = "pageSize", required = false, defaultValue =
            "20") Integer pageSize) {
        List<String> activityIds = corgiFavorActivityService.getActivity(userId, (page - 1) * pageSize, pageSize);
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(activityIds);
        List<CorgiActivityDetail> detailList = convertDetail(activityList, hasUserId() ? getUserId() : userId);
        return new JsonResult(detailList);
    }

    @GetMapping("get_liked_activity")
    public JsonResult getLikedActivity(@RequestParam("userId") String userId,
                                       @RequestParam(required = false, name = "activityId") String activityId,
                                       @RequestParam(required = false, name = "category") String category,
                                       @RequestParam(required = false, name = "page", defaultValue = "1") Integer page,
                                       @RequestParam("pageSize") Integer pageSize) {
        List<CorgiActivity> corgiActivities = new ArrayList<>();
        List<String> activityIds = corgiLikeService.getLikedActivity(userId, activityId, category, page, pageSize);
        if (!CollectionUtils.isEmpty(activityIds)) {
            corgiActivities = corgiActivityService.getActivityByIds(activityIds);
        }
        return new JsonResult(convertDetail(corgiActivities, getUserId(), false));
    }

    @PostMapping("/share")
    public JsonResult addShareActivity(@RequestBody ActivityShare activityShare) {
        activityShare.setShareUserId(getUserId());
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityShare.getActivityId()));
        if (!CollectionUtils.isEmpty(activityList) && activityList.get(0).getUserId() != null) {
            activityShare.setUserId(activityList.get(0).getUserId());
            corgiShareService.addShare(activityShare);
        }
        return new JsonResult();
    }

    @GetMapping("/call_city")
    public JsonResult callCity(@RequestParam("city") String city, @RequestParam("activityId") String activityId) {
        String LockKey = "call_city_lock_" + getUserId();
        if (!corgiUtilService.tryLock(LockKey, "1", 20L, TimeUnit.SECONDS)) {
            return new JsonResult();
        }
        log.info("city call... ", city);
        if (!"69548".equals(getUserId())) {
            String key = getCallCityKey(getUserId());
            if (key == null) {
                return new JsonResult(Constants.API_ERROR_CODE, "这周一呼百应次数已超过三次");
            }
            redisTemplate.opsForValue().set(key, System.currentTimeMillis() + "", 7, TimeUnit.DAYS);
        }
        HashMap extra = new HashMap();
        UserDetail userDetail = corgiUserService.getUserDetail(getUserId(), null);
        extra.put("type", 902);
        extra.put("city", city);
        extra.put("activityId", activityId);
        if (populateExtra(extra, activityId, getUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.CITY)
                    .message(userDetail.getNickname().concat("正在召集本地小伙伴参加活动！"))
                    .sourceUserId(getUserId())
                    .extra(extra)
                    .build());
        }
        return new JsonResult();
    }

    @GetMapping("/call_activity_city")
    public JsonResult callActivityCity(@RequestParam("activityId") String activityId, @RequestParam("city") String city) {
        String LockKey = "call_activity_city_lock_" + getUserId();
        if (!corgiUtilService.tryLock(LockKey, "1", 20L, TimeUnit.SECONDS)) {
            return new JsonResult();
        }
        HashMap extra = new HashMap();
        UserDetail userDetail = corgiUserService.getUserDetail(getUserId(), null);
        extra.put("activityId", activityId);
        extra.put("city", city);
        extra.put("type", 902);
        if (populateExtra(extra, activityId, getUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.ACTIVITY + "_city")
                    .sourceUserId(getUserId())
                    .message(userDetail.getNickname() + "发起了一个活动，快去看看吧")
                    .extra(extra)
                    .build());
        }
        return new JsonResult();
    }

    @GetMapping("/call_activity_end")
    public JsonResult callActivityEnd(@RequestParam("activityId") String activityId) {
        HashMap extra = new HashMap();
        extra.put("activityId", activityId);
        extra.put("type", 903);
        if (populateExtra(extra, activityId, getUserId())) {
            extra.put("content", "被官方评为精品内容将享受高曝光");
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.ACTIVITY + "_end")
                    .sourceUserId(getUserId())
                    .message("活动结束了，快分享没好瞬间吧！")
                    .extra(extra)
                    .build());
        }
        return new JsonResult();
    }

    @GetMapping("get_ref_activity")
    public JsonResult getRefActivity(@RequestParam("activityId") String activityId, @RequestParam(required = false, name = "page", defaultValue = "1") Integer page, @RequestParam(required = false, name = "pageSize", defaultValue = "20") Integer size) {
        CorgiActivity search = new CorgiActivity();
        search.setRefActivityId(activityId);
        List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(search, page, size);
        return new JsonResult(convertDetail(activityList, getUserId()));
    }


    @GetMapping("uninterested")
    public JsonResult Uninterested(@RequestParam("creatorId") String creatorId, @RequestParam("activityId") String activityId) {
        corgiBlacklistService.addUninterested(getUserId(), activityId, creatorId);
        String blackKey = "black_cache_" + getUserId();
        if (redisTemplate.hasKey(blackKey)) {
            redisTemplate.opsForList().leftPush(blackKey, creatorId);
        }
        return new JsonResult();
    }


    private boolean populateExtra(HashMap extra, String activityId, String userId) {
        extra.put("aId", activityId);
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
        if (corgiActivities != null && corgiActivities.size() > 0 && corgiActivities.get(0) != null) {
            CorgiActivity activity = corgiActivities.get(0);
            if (userId != null && !userId.equals(activity.getUserId())) {
                return false;
            }
            extra.put("title", activity.getTitle());
            extra.put("desc", activity.getContent());
            if (!StringUtils.isEmpty(activity.getCity())) {
                extra.put("city", activity.getCity());
            }
            List<ActivityPic> pics = corgiPicService.getActivityPic(activityId);
            if (!CollectionUtils.isEmpty(pics) && pics.get(0) != null) {
                extra.put("picUrl", activity.getPics().get(0).getPicUrl());
            }
            return true;
        }
        return false;
    }

    @GetMapping("test")
    public JsonResult test() {
        corgiFavorActivityService.addFavor("1", "aaaa");
        corgiFavorActivityService.addFavor("1", "bbbb");
        corgiFavorActivityService.addFavor("1", "cccc");
        corgiFavorActivityService.deleteFavor("1", "cccc");
        log.info("check" + corgiFavorActivityService.countActivity("1", "bbbb"));
        corgiFavorActivityService.getActivity("1", 1, 20);
        corgiActivityService.getUserRunningActivity("1", 1, 20);
        corgiActivityService.getUserRunningActivity("1", 1, 20);
        return new JsonResult();
    }

    private List<LikedActivity> covertLiked(List<CorgiActivity> activities) {
        List<LikedActivity> likedActivities = new ArrayList<>();
        for (CorgiActivity corgiActivity : activities) {
            LikedActivity likedActivity = new LikedActivity();
            likedActivity.setActivityId(corgiActivity.getId());
            likedActivity.setCategory(corgiActivity.getCategory());
            if (!CollectionUtils.isEmpty(corgiActivity.getPics()) && !StringUtils.isEmpty(corgiActivity.getPics().get(0).getPicUrl())) {
                if (corgiActivity.getHeight() == null || corgiActivity.getWidth() == null) {
                    String picUrl = corgiActivity.getPics().get(0).getPicUrl();
                    likedActivity.setPicUrl(picUrl);
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    Long height = picInfo.getHeight();
                    Long width = picInfo.getWidth();
                    likedActivity.setHeight(height);
                    likedActivity.setWidth(width);
                } else {
                    likedActivity.setHeight(corgiActivity.getHeight());
                    likedActivity.setWidth(corgiActivity.getWidth());
                }
            }
            if (CorgiActivity.CAT_VIDEO.equals(corgiActivity.getCategory())) {
                likedActivity.setHeight(corgiActivity.getHeight());
                likedActivity.setWidth(corgiActivity.getWidth());
                if (StringUtils.isEmpty(likedActivity.getPicUrl())) {
                    likedActivity.setPicUrl(corgiActivity.getCoverUrl());
                }
            }
            likedActivities.add(likedActivity);
        }
        return likedActivities;
    }

    private List<FollowShowActivity> convertFollowActivity(List<String> activityIds, String userId) {
        if (CollectionUtils.isEmpty(activityIds)) {
            return new ArrayList<>();
        }
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(activityIds);
        if (CollectionUtils.isEmpty(activityList)) {
            return new ArrayList<>();
        }
        List<FollowShowActivity> activities = new ArrayList<>();
        List<String> userIds = new ArrayList<>();
        for (CorgiActivity activity : activityList) {
            String creator = activity.getUserId();
            if (creator.equals(userId)) {
                continue;
            }
            if (activity == null || activity.getUserId() == null
                    || ("fail".equals(activity.getCheckStatus()) || "check".equals(activity.getCheckStatus()))) {
                continue;
            }
            if (!"AppStore".equals(RequestUtil.getChannel()) && ("check".equals(activity.getStrictStatus()) || CorgiActivity.CAT_VIDEO.equals(activity.getCategory()))) {
                continue;
            }
            if (AliyunGreenService.NOT_GOOD.equals(activity.getCheckStatus())) {
                continue;
            }
            if (userIds.contains(creator)) {
                continue;
            }
            if (redisTemplate.hasKey("browse_paying-" + activity.getId() + "-" + getUserId())) {
                continue;
            }
            userIds.add(creator);
            UserDetail detail = corgiUserService.getUserDetailBasic(creator);
            if (detail != null) {
                FollowShowActivity showActivity = new FollowShowActivity();
                showActivity.setUserId(creator);
                showActivity.setNickname(detail.getNickname());
                showActivity.setActivityId(activity.getId());
                showActivity.setAvatar(detail.getAvatar());
                activities.add(showActivity);
            }
        }
        return activities;
    }

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList, String userId) {
        return convertDetail(activityList, userId, false);
    }

    private void checkComment(String userId) {
        for (int i = 0; i < 2; i++) {
            String key = "ad_comment_" + userId + "-" + i;
            if (!redisTemplate.hasKey(key)) {
                redisTemplate.opsForValue().set(key, "1", 1l, TimeUnit.HOURS);
                return;
            }
        }
        redisTemplate.opsForValue().set("darkroom_" + userId, "1", 1l, TimeUnit.DAYS);
    }

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList, String userId, boolean showNotGood) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Long nowTime = System.currentTimeMillis();
            Iterator<CorgiActivity> it = activityList.iterator();
            while (it.hasNext()) {
                CorgiActivity activity = it.next();
                if (activity == null || activity.getUserId() == null || (!userId.equals(activity.getUserId()) && ("fail".equals(activity.getCheckStatus()) || "check".equals(activity.getCheckStatus())))) {
                    it.remove();
                    continue;
                }
                if (!(CorgiActivity.CAT_VIDEO.equals(activity.getCategory())
                        || CorgiActivity.CAT_TEXT.equals(activity.getCategory())
                        || CorgiActivity.CAT_PAYING.equals(activity.getCategory())) && CollectionUtils.isEmpty(activity.getPics())) {
                    it.remove();
                    continue;
                }
                if (!"AppStore".equals(RequestUtil.getChannel()) && ("check".equals(activity.getStrictStatus()) || CorgiActivity.CAT_VIDEO.equals(activity.getCategory())) && !getUserId().equals(activity.getUserId())) {
                    it.remove();
                    continue;
                }
                if (!showNotGood && !userId.equals(activity.getUserId()) && AliyunGreenService.NOT_GOOD.equals(activity.getCheckStatus())) {
                    it.remove();
                    continue;
                }
                List<UserDetail> buyers = new ArrayList<>();
                if (CorgiActivity.CAT_PAYING.equals(activity.getCategory())) {
                    if (activity.getCoverUrl() != null && !activity.getCoverUrl().contains("?x-oss-process") && StringUtils.isEmpty(activity.getVideoId())) {
                        activity.setCoverUrl(activity.getCoverUrl() + "?x-oss-process=style/fuzzyCover");
                    }
//                    else {
//                        activity.setCoverUrl("https://corgi-pic.oss-cn-beijing.aliyuncs.com/corgi/WechatIMG4084.jpg");
//                    }
                    activity.setRefActivityPic(activity.getCoverUrl());
                    List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                            .traderId(activity.getUserId())
                            .goodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY)
                            .goodsId(activity.getId())
                            .start(0)
                            .size(3)
                            .build());
                    if (!CollectionUtils.isEmpty(goods) || activity.getUserId().equals(getUserId())) {
                        for (CorgiUserGoods good : goods) {
                            UserDetail d = corgiUserService.getUserDetailBasic(good.getUserId());
                            if (d != null) {
                                buyers.add(d);
                            }
                        }
                        if (!activity.getUserId().equals(getUserId()) && (!hasUserId() || CollectionUtils.isEmpty(corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                                .userId(getUserId())
                                .traderId(activity.getUserId())
                                .goodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY)
                                .start(0)
                                .size(1)
                                .goodsId(activity.getId())
                                .build())))) {
                            activity.setPics(new ArrayList<>());
                            activity.setVideoUrl("");
                            activity.setStatus("unpay");
                        } else {
                            if (!CollectionUtils.isEmpty(activity.getPics())) {
                                String picUrl = activity.getPics().get(0).getPicUrl();
                                activity.setCoverUrl(picUrl);
                            } else {
                                activity.setCoverUrl(activity.getCoverUrl().replaceAll("\\?x-oss-process=style/fuzzyCover", ""));
                            }
                        }
                    } else {
                        activity.setPics(new ArrayList<>());
                        activity.setVideoUrl("");
                        activity.setStatus("unpay");
                    }
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
                Long commentCount = null;
                if ("AppStore".equals(RequestUtil.getChannel())) {
                    commentCount = corgiCommentService.countActivityComment(activity.getId());
                }
                Integer swiftCommentCount = corgiCommentService.countActivityCommentByStatus(activity.getId(), getUserId(), ActivityComment.SWIFT);
                Long likeCount = corgiLikeService.countActivityLike(activity.getId());
                List<ActivityLike> users = corgiLikeService.getActivityLike(activity.getId(), 1, 3);
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), getUserId());
                List<UserProfile> signUpUsers = new ArrayList<>();
                Integer shareCount = corgiShareService.countShare(activity.getId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initSize(height, width)
                        .initCommentCount(commentCount)
                        .initLikeCount(likeCount)
                        .initLikeUsers(users)
                        .initSignUpUsers(signUpUsers)
                        .hasLike(hasLike);
                detail.setBuyers(buyers);
                detail.setTimeShow(TimeUtil.buildTimeText(detail.getCreateTime(), nowTime, sdf));
                if ("AppStore".equals(RequestUtil.getChannel())) {
                    ActivityComment activityComment = corgiCommentService.getLastComment(activity.getId(), getUserId());
                    detail.setLastComment(activityComment);
                }
                detail.setShareCount(shareCount);
                detail.setHasSwiftComment(swiftCommentCount);
                if (!StringUtils.isEmpty(activity.getMerchId())) {
                    detail.setMerchandise(corgiOrderService.getMerchandiseById(activity.getMerchId(), getUserId()));
                }
                if (!StringUtils.isEmpty(activity.getUserId()) && (activity.getUserId().startsWith("B") || activity.getUserId().startsWith("C"))) {
                    detail.setBarId(detail.getUserId());
                    detail.setUserId(null);
                    BarProfile profile = corgiBarService.getBarProfile(detail.getBarId());
                    detail.setBarDetail(profile);
                } else if (!StringUtils.isEmpty(activity.getUserId())) {
                    UserDetail userDetail = corgiUserService.getUserDetail(activity.getUserId(), null);
                    detail.setUserDetail(corgiUtilService.checkUserDetail(userDetail, userId));
                    detail.setIsFollowed(corgiUserFollowService.isFollowed(userId, activity.getUserId()));
                }
                detailList.add(detail);
            }
        }
        return detailList;
    }

    private boolean checkActivityUser(String activityId, String userId) {
        if (StringUtils.isEmpty(userId)) {
            return true;
        }
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
        if (!CollectionUtils.isEmpty(activityList)) {
            CorgiActivity activity = activityList.get(0);
            return userId.equals(activity.getUserId());
        }
        return true;
    }

    private boolean checkActivityPic(List<ActivityPic> list) {
        if (CollectionUtils.isEmpty(list)) {
            return true;
        }
        for (CorgiPic pic : list) {
            if ("check".equals(pic.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private List<CorgiActivity> mergeActivity
            (List<CorgiActivity> activityList, List<CorgiActivity> businessList) {
        List<Integer> takenPositions = new ArrayList<>();
        Random random = new Random();
        int bound = activityList.size();
        for (CorgiActivity business : businessList) {
            int position = random.nextInt(bound);
            int index = findPosition(position, bound, takenPositions);
            activityList.add(index, business);
        }
        return activityList;
    }

    //将商户活动随机混入人员活动中，商户活动不能连续
    private int findPosition(int oldPosition, int bound, List<Integer> takenPositions) {
        int step = oldPosition <= bound / 2 ? 1 : -1;
        for (int i = 0; i < takenPositions.size(); i++) {
            if (takenPositions.contains(oldPosition)) {
                oldPosition += step;
            } else {
                break;
            }
        }
        takenPositions.add(oldPosition);

        int offset = 0;
        for (Integer takenPosition : takenPositions) {
            if (takenPosition < oldPosition) {
                offset++;
            }
        }
        return oldPosition + offset;
    }

    private String getCallCityKey(String userId) {
        for (int i = 1; i <= 3; i++) {
            String key = CALL_CITY_PREFIX.concat(i + "_").concat(userId);
            if (!redisTemplate.hasKey(key)) {
                return key;
            }
        }
        return null;
    }

    private boolean checkRate(String userId, String type, int size) {
        String keyRate = "add_activity_rate-" + type + "-" + userId;
        String keyList = "add_activity_list-" + type + "-" + userId;
        if (redisTemplate.opsForValue().setIfAbsent(keyRate, "1", 24L, TimeUnit.HOURS)) {
            redisTemplate.delete(keyList);
            redisTemplate.opsForList().rightPush(keyList, "0");
            redisTemplate.expire(keyList, 24L, TimeUnit.HOURS);
            return true;
        }
        Long sizeNow = redisTemplate.opsForList().size(keyList);
        if (sizeNow >= size) {
            return false;
        }
        redisTemplate.opsForList().rightPush(keyList, sizeNow + "");
        return true;
    }
}
