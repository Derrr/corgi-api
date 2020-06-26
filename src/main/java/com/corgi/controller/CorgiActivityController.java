package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.*;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
import com.corgi.entity.*;
import com.corgi.entity.tool.AddAttendResult;
import com.corgi.exception.PermissionException;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
    private CorgiUserMatchService corgiUserMatchService;
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
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    private static Comparator<CorgiActivityDetail> detailComparator = (o1, o2) -> o2.getMatch().compareTo(o1.getMatch());

    private static Comparator<CorgiActivityDetail> timeComparator = (a1, a2) -> {
        String c1 = a1.getCreateTime() == null ? "" : a1.getCreateTime();
        String c2 = a2.getCreateTime() == null ? "" : a2.getCreateTime();
        return c2.compareTo(c1);
    };

    //private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    //private static SimpleDateFormat sdf_simple = new SimpleDateFormat("yyyy/MM/dd");

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        activity.setCategory(CorgiActivity.CAT_ACTIVITY);
        log.info("user {} adding activity", activity.getUserId());
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkActivity(activity);
        if (checkDuplicateActivity(activity)) {
            return new JsonResult(Constants.API_ERROR_CODE, "抱歉，同一内容不可重复发布");
        }
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getId(), CheckPic.ACTIVITY);
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        List<CorgiActivity> corgiActivities = corgiActivityService.getSimilarActivity(activity);
        if (!CollectionUtils.isEmpty(corgiActivities)) {
            Iterator<CorgiActivity> it = corgiActivities.iterator();
            while (it.hasNext()) {
                CorgiActivity corgiActivity = it.next();
                if (corgiActivity.getId().equals(activity.getId())) {
                    it.remove();
                }
            }
        }
        List<CorgiActivityDetail> details = convertDetail(corgiActivities, activity.getUserId());
        long count = corgiActivityService.countUserActivity(activity.getUserId());
        redisTemplate.delete("activity_count_" + activity.getUserId());
        HashMap extra = new HashMap();
        extra.put("activityId", activity.getId());
        extra.put("type", PushMessage.ACTIVITY_MESSAGE_TYPE);
        mqService.sendMessage(PushMessage.builder()
                .type(PushMessage.ACTIVITY)
                .sourceUserId(activity.getUserId())
                .message(PushMessage.ACTIVITY_MESSAGE)
                .extra(extra)
                .build());
        return new JsonResult(AddActivityResult.getResult(activity).setSimilar(details).setCount(count));
    }

    @PostMapping("attend")
    public JsonResult attend(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        if (redisTemplate.hasKey("activity_attended_" + activity.getUserId())) {
            return new JsonResult(Constants.API_ERROR_CODE, "打卡太频繁了哦");
        }
        String now = System.currentTimeMillis() + "";
        redisTemplate.opsForValue().set("activity_attended_" + activity.getUserId(), now, 2L, TimeUnit.SECONDS);
        Integer addResult = -1;
        corgiUtilService.lock("attending_" + activity.getUserId());
        try {
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
        } finally {
            corgiUtilService.unlock("attending_" + activity.getUserId());
        }
        return new JsonResult(AddAttendResult.getResult(activity, addResult));
    }

    @GetMapping("get_attendances")
    public JsonResult getAttendance(@RequestParam(name = "date", required = false) String date, @RequestParam("barId") String barId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiActivity search = new CorgiActivity();
        search.setBarId(barId);
        if (!StringUtils.isEmpty(date)) {
            search.setCreateTime(date);
        }
        List<CorgiActivity> corgiActivities = corgiActivityService.searchCorgiActivity(search, page, pageSize);
        return new JsonResult(corgiActivities);
    }

    @GetMapping("get_heat_attendances")
    public JsonResult getHeatAttendance(@RequestParam("barId") String barId) {
        CorgiActivity search = new CorgiActivity();
        search.setBarId(barId);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);
        search.setCreateTime(new SimpleDateFormat("yyyy-MM-dd").format(calendar.getTime()));
        List<String> activityIds = corgiUserActivityService.getHeatActivity(search, 1, 18);
        return new JsonResult(corgiActivityService.getActivityByIds(activityIds));
    }


    @PostMapping("add_image_activity")
    public JsonResult addImageActivity(@RequestBody CorgiActivity activity) {
        if (hasUserId()) {
            activity.setUserId(getUserId());
        }
        if (redisTemplate.hasKey("activity_sent_" + activity.getUserId())) {
            return new JsonResult(Constants.API_ERROR_CODE, "发送太频繁了哦");
        }
        redisTemplate.opsForValue().set("activity_sent_" + activity.getUserId(), System.currentTimeMillis() + "", 2L, TimeUnit.SECONDS);
        activity.setCategory(CorgiActivity.CAT_IMAGE);
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkImageActivity(activity);
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), activity.getId(), CheckPic.ACTIVITY);
        if (!checkActivityPic(activityPics)) {
            activity.setCheckStatus(AliyunGreenService.CHECK);
        }
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        return new JsonResult(AddActivityResult.getResult(activity));
    }

    @PostMapping("add_comment")
    public JsonResult addComment(@RequestBody ActivityComment activityComment) {
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityComment.getActivityId()));
        if (CollectionUtils.isEmpty(activityList)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "评论失败，活动不存在");
        }
        if (!aliyunGreenService.checkText(activityComment.getContent())) {
            boolean noFilterContent = StringUtils.isEmpty(AliyunGreenService.Filtered_Content.get());
            activityComment.setContent(noFilterContent ? activityComment.getContent().replaceAll(".", "*") : AliyunGreenService.Filtered_Content.get());
        }
        activityComment.setUserId(activityList.get(0).getUserId());
        if (hasUserId()) {
            activityComment.setCommentUserId(getUserId());
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
                    .message(PushMessage.NEW_MESSAGE)
                    .extra(extra)
                    .build());
        }
        if (!StringUtils.isEmpty(activityComment.getReplyUserId())
                && !activityComment.getReplyUserId().equals(activityComment.getCommentUserId())) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(activityComment.getCommentUserId())
                    .targetUserId(activityComment.getReplyUserId())
                    .message(PushMessage.NEW_MESSAGE)
                    .extra(extra)
                    .build());
        }
        return new JsonResult(activityComment);
    }

    @PostMapping("like")
    public JsonResult like(@RequestBody ActivityLike activityLike) {
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityLike.getActivityId()));
        if (CollectionUtils.isEmpty(activityList)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "点赞失败，活动不存在");
        }
        activityLike.setUserId(activityList.get(0).getUserId());
        activityLike.setLikeUserId(getUserId());
        corgiLikeService.addActivityLike(activityLike);
        HashMap extra = new HashMap();
        extra.put("activityId", activityLike.getActivityId());
        extra.put("type", PushMessage.LIKE_COMMENT_TYPE);
        if (!activityLike.getUserId().equals(activityLike.getLikeUserId().equals(activityLike.getLikeUserId()))) {
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.DEFAULT)
                    .sourceUserId(activityLike.getLikeUserId())
                    .targetUserId(activityLike.getUserId())
                    .message(PushMessage.NEW_MESSAGE)
                    .extra(extra)
                    .build());
        }
        return new JsonResult();
    }

    @GetMapping("unlike")
    public JsonResult unlike(@RequestParam("activityId") String activityId) {
        corgiLikeService.deleteActivityLike(getUserId(), activityId);
        return new JsonResult();
    }

    @GetMapping("delete_comment")
    public JsonResult deleteComment(@RequestParam("commentId") String commentId) {
        corgiCommentService.deleteActivityComment(commentId);
        return new JsonResult();
    }

    @GetMapping("get_comments")
    public JsonResult getComment(@RequestParam("activityId") String activityId) {
        List<ActivityComment> activityComments = corgiCommentService.getActivityComment(activityId);
        return new JsonResult(activityComments);
    }

    @GetMapping("get_likes")
    public JsonResult getLike(@RequestParam("activityId") String activityId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<ActivityLike> activityLikes = corgiLikeService.getActivityLike(activityId, page, pageSize);
        return new JsonResult(activityLikes);
    }

    @GetMapping("count_comment")
    public JsonResult countComment(@RequestParam("activityId") String activityId) {
        Long count = corgiCommentService.countActivityComment(activityId);
        return new JsonResult(count);
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
    public JsonResult getActivityMessage(@RequestParam("pageSize") Integer pageSize) {
        List<ActivityMessage> activityMessages = corgiToolService.getActivityMessage(getUserId(), pageSize);
        return new JsonResult(activityMessages);
    }

    @GetMapping("get_all_activity_message")
    public JsonResult getAllActivityMessage(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<ActivityMessage> activityMessages = corgiToolService.getAllActivityMessage(getUserId(), page, pageSize);
        return new JsonResult(activityMessages);
    }

    @GetMapping("get_is_like")
    public JsonResult getIsLike(@RequestParam("activityId") String activityId) {
        Integer isLike = corgiLikeService.countUserLike(activityId, getUserId());
        return new JsonResult(isLike);
    }


    @GetMapping("count_activity_message")
    public JsonResult countActivityMessage() {
        ActivityMessageCount messageCount = new ActivityMessageCount();
        Long count = corgiToolService.countActivityMessage(getUserId());
        messageCount.setCount(count);
        if (count != null && count > 0) {
            ActivityMessage activityMessage = corgiToolService.getLastActivityMessage(getUserId());
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

    private boolean checkDuplicateActivity(CorgiActivity activity) {
        CorgiActivity queryActivity = new CorgiActivity();
        queryActivity.setStatus(CorgiActivity.CREATED);
        queryActivity.setUserId(activity.getUserId());
        if (AliyunGreenService.TEXT_FORBIDDEN.equals(activity.getTitle())) {
            queryActivity.setCheckTitle(activity.getCheckTitle());
        } else {
            queryActivity.setTitle(activity.getTitle());
        }
        long result = corgiActivityService.countCorgiActivity(queryActivity);
        return result > 0;
    }

    @GetMapping("test_add_activity")
    public JsonResult testAddActivity() {
        CorgiActivity activity = new CorgiActivity();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        Random random = new Random();
        activity.setCreateTime(sdf.format(new Date()));
        activity.setStatus(CorgiActivity.CREATED);
        activity.setLat(random.nextDouble());
        activity.setLng(random.nextDouble());
        activity.setActivityType("test");
        activity.setAddress("阿维机构啊叫，给皮卡金额");
        activity.setBudget(124);
        activity.setContent("阿我诶咕叽咕叽哦可刺激噶我");
        activity.setPayType("AA");
        activity.setTitle("测试");
        activity.setPeopleCount(341);
        activity.setSignUpTime("2020/11/11 00:00");
        ActivityPic pic1 = new ActivityPic();
        pic1.setPicUrl("https://corgi-pic.oss-cn-beijing.aliyuncs.com/avatar/2/1577412815326");
        ActivityPic pic2 = new ActivityPic();
        pic2.setPicUrl("https://corgi-pic.oss-cn-beijing.aliyuncs1.com/avatar/2/1577412815326");
        activity = corgiActivityService.addCorgiActivity(activity);
        activity.setTitle("测试34");
        corgiActivityService.updateCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @PostMapping("update_activity")
    public JsonResult updateActivity(@RequestBody CorgiActivity activity) throws PermissionException {
        if (hasUserId()) {
            log.info("into update_activity..." + getUserId());
            if (!checkActivityUser(activity.getId(), getUserId())) {
                throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
            }
        }
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkActivity(activity);
        activity = corgiActivityService.updateCorgiActivity(activity);
        corgiUserActivityService.deleteSignUpByActivity(activity.getId());
        if (CorgiActivity.FULL.equals(activity.getStatus())) {
            activity.setStatus(CorgiActivity.CREATED);
            corgiActivityService.updateCorgiActivityStatus(activity);
        }
        return new JsonResult(activity);
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
        corgiActivityService.deleteCorgiActivity(activityId);
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
            return new JsonResult(Constants.API_ERROR_CODE, "活动不存在");
        }
        HashMap extra = new HashMap();
        extra.put("activityId", activityId);
        extra.put("type", PushMessage.SIGN_UP_MESSAGE_TYPE);
        mqService.sendMessage(PushMessage.builder()
                .sourceUserId(userId)
                .targetUserId(corgiActivities.get(0).getUserId())
                .extra(extra)
                .message(PushMessage.SIGN_UP_MESSAGE)
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
                return new JsonResult(Constants.API_ERROR_CODE, "活动不存在");
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
                return new JsonResult(Constants.API_ERROR_CODE, "活动不存在");
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
        if (CollectionUtils.isEmpty(corgiActivities)) {
            return new JsonResult(Constants.API_ERROR_CODE, "活动不存在");
        }
        CorgiActivity activity = corgiActivities.get(0);
        CorgiActivityDetail detail = convertDetail(Arrays.asList(activity), userId).get(0);
        List<CorgiActivity> similarActivities = corgiActivityService.getSimilarActivity(activity);
        if (!CollectionUtils.isEmpty(similarActivities)) {
            Iterator<CorgiActivity> it = similarActivities.iterator();
            while (it.hasNext()) {
                CorgiActivity corgiActivity = it.next();
                if (activityId.equals(corgiActivity.getId())) {
                    it.remove();
                }
            }
        }
        List<CorgiActivityDetail> similarActivity = convertDetail(similarActivities, userId);
        detail.setSimilarActivity(similarActivity);
        return new JsonResult(detail);
    }

    @GetMapping("get_follow_activity")
    public JsonResult getFollowActivity(@RequestParam("userId") String userId, @RequestParam(name = "page", defaultValue = "1") Integer page, @RequestParam(name = "size", defaultValue = "20") Integer size) {
        List<String> userIds = corgiUserFollowService.getFollowUser(userId);
        if (CollectionUtils.isEmpty(userIds)) {
            return new JsonResult();
        }
        List<CorgiActivity> corgiActivities;
        if (hasVersion()) {
            corgiActivities = corgiActivityService.getAllActivityByUserIds(getUserId(), userIds, CorgiActivity.CREATED, page, size);
        } else {
            corgiActivities = corgiActivityService.getActivityByUserIds(userIds, CorgiActivity.CREATED, page, size);
        }
        return new JsonResult(convertDetail(corgiActivities, userId));
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
                return new JsonResult(Constants.API_ERROR_CODE, "活动不存在");
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


    @GetMapping("get_range_activity")
    public JsonResult getRangeActivity(@RequestParam("userId") String userId, @RequestParam(name = "lng", required = false, defaultValue = "0") double lng, @RequestParam(name = "lat", required = false, defaultValue = "0") double lat, @RequestParam(name = "range", required = false, defaultValue = "0") double range, ActivityQuery activityQuery) {
        if (StringUtils.isEmpty(activityQuery.getUserId())) {
            activityQuery.setUserId(userId);
        }
        if (hasVersion()) {
            activityQuery.setVersion("1.4.0");
        }
        activityQuery.setGroup(CorgiUserController.changeGroupList(activityQuery.getGroup()));
        activityQuery.setPreferGroup(CorgiUserController.changeGroupList(activityQuery.getPreferGroup()));
        List<CorgiActivity> activityList = corgiActivityService.getCorgiActivityByRange(lng, lat, range, activityQuery);
        List<CorgiActivityDetail> detailList = convertDetail(activityList, userId);
        if (ActivityQuery.SORT_MATCH.equals(activityQuery.getSort())) {
            detailList.sort(detailComparator);
        } else if (ActivityQuery.SORT_TIME.equals(activityQuery.getSort()) || StringUtils.isEmpty(activityQuery.getSort())) {
            detailList.sort(timeComparator);
        }
        mqService.sendTrace(TraceFollow.builder()
                .userId(userId)
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.ACTIVITY)
                .build());
        return new JsonResult(detailList);
    }

    @GetMapping("get_user_activity")
    public JsonResult getMyRunningActivity(@RequestParam("userId") String userId, @RequestParam(name = "status", required = false) String status, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<? extends CorgiActivity> result;
        if (CorgiActivity.CREATED.equals(status)) {
            if (hasVersion()) {
                result = convertDetail(corgiActivityService.getUserAllRunningActivity(userId, page, pageSize), getUserId());
            } else {
                result = corgiActivityService.getUserRunningActivity(userId, page, pageSize);
            }
        } else {
            if (hasVersion()) {
                result = corgiActivityService.getAllActivityByUserIds(getUserId(), Arrays.asList(userId), "", (page - 1) * pageSize, pageSize);
            } else {
                result = corgiActivityService.getActivityByUserIds(Arrays.asList(userId), "", (page - 1) * pageSize, pageSize);
            }
        }
        return new JsonResult(result);
    }

    @GetMapping("get_user_running_activity")
    public JsonResult getUserRunningActivity(@RequestParam("userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiActivity> result = corgiActivityService.getUserRunningActivity(userId, page, pageSize);
        return new JsonResult(result);
    }


    @GetMapping("/delete_activity_pic")
    public JsonResult deleteUserPic(@RequestParam("picId") String picId) {
        String result = corgiPicService.deleteActivityPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/add_activity_pic")
    public JsonResult addUserPic(@RequestBody List<ActivityPic> activityPics) {
        if(CollectionUtils.isEmpty(activityPics) || activityPics.get(0) == null){
            return new JsonResult();
        }
        String activityId = activityPics.get(0).getActivityId();
        List<ActivityPic> activityPicList = (List<ActivityPic>) aliyunGreenService.checkPic(activityPics , activityId, CheckPic.ACTIVITY);
        String status = AliyunGreenService.PASS;
        for(ActivityPic pic: activityPicList) {
            String result = corgiPicService.addActivityPic(pic);
            pic.setPicId(result);
            if(AliyunGreenService.CHECK.equals(pic.getStatus())){
                status = pic.getStatus();
            }
        }
        if(AliyunGreenService.CHECK.equals(status)) {
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
        mqService.sendTrace(TraceFollow.builder()
                .userId(userId)
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.FAVOR)
                .build());
        return new JsonResult(detailList);
    }

    @GetMapping("get_liked_activity")
    public JsonResult getLikedActivity(@RequestParam("userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        log.info("into activity....");
        List<CorgiActivity> corgiActivities = new ArrayList<>();
        List<String> activityIds = corgiLikeService.getLikedActivity(userId, page, pageSize);
        log.info("activityIds..." + activityIds);
        if (!CollectionUtils.isEmpty(activityIds)) {
            corgiActivities = corgiActivityService.getActivityByIds(activityIds);
        }
        return new JsonResult(covertLiked(corgiActivities));
    }

    @PostMapping("share")
    public JsonResult addShareActivity(@RequestBody ActivityShare activityShare) {
        activityShare.setShareUserId(getUserId());
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityShare.getActivityId()));
        if (!CollectionUtils.isEmpty(activityList) && activityList.get(0).getUserId() != null) {
            activityShare.setUserId(activityList.get(0).getUserId());
            corgiShareService.addShare(activityShare);
        }
        return new JsonResult();
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
                String picUrl = corgiActivity.getPics().get(0).getPicUrl();
                likedActivity.setPicUrl(picUrl);
                PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                Integer height = picInfo.getHeight();
                Integer width = picInfo.getWidth();
                likedActivity.setHeight(height);
                likedActivity.setWidth(width);
            }
            likedActivities.add(likedActivity);
        }
        return likedActivities;
    }


    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList, String userId) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            for (CorgiActivity activity : activityList) {
                activity.setCurrentTime(now);

                Integer height = 0;
                Integer width = 0;
                if (!CollectionUtils.isEmpty(activity.getPics())) {
                    String picUrl = activity.getPics().get(0).getPicUrl();
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    height = picInfo.getHeight();
                    width = picInfo.getWidth();
                }
                Integer signUp = corgiUserActivityService.getStatus(userId, activity.getId());
                double match = corgiUserMatchService.getUserMatch(userId, activity.getUserId());
                Long commentCount = corgiCommentService.countActivityComment(activity.getId());
                Long likeCount = corgiLikeService.countActivityLike(activity.getId());
                List<ActivityLike> users = corgiLikeService.getFollowUser(getUserId(), activity.getId());
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), getUserId());
                Integer signUpCount = corgiUserActivityService.countUsers(activity.getId(), null);
                List<UserProfile> signUpUsers = new ArrayList<>();
                if (signUpCount != null && signUpCount > 0 && signUpCount <= 3) {
                    signUpUsers = corgiUserActivityService.getUsers(activity.getId(), null, null);
                } else if (signUpCount != null && signUpCount > 3) {
                    signUpUsers = corgiUserActivityService.getPopularUsers(activity.getId(), null);
                }
                Integer shareCount = corgiShareService.countShare(activity.getId());
                ActivityComment activityComment = corgiCommentService.getLastComment(activity.getId(), getUserId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initMatch(match)
                        .initSize(height, width)
                        .initSignUpStatus(signUp)
                        .initCommentCount(commentCount)
                        .initLikeCount(likeCount)
                        .initLikeUsers(users)
                        .initSignUpUsers(signUpUsers)
                        .hasLike(hasLike);
                detail.setLastComment(activityComment);
                detail.setSignUpCount(signUpCount);
                detail.setShareCount(shareCount);
                if (!StringUtils.isEmpty(detail.getBarId() != null)) {
                    BarProfile profile = corgiBarService.getBarProfile(detail.getBarId());
                    detail.setBarDetail(profile);
                }
                if (!StringUtils.isEmpty(activity.getUserId())) {
                    UserDetail userDetail = corgiUserService.getUserDetail(activity.getUserId(), null);
                    detail.setUserDetail(userDetail);
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
}
