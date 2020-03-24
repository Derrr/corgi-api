package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.*;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
import com.corgi.entity.*;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserProfile;
import com.corgi.user.entity.UserSignUp;
import lombok.extern.slf4j.Slf4j;
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
    private CorgiUserService corgiUserService;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private MQService mqService;

    private static Comparator<CorgiActivityDetail> detailComparator = (o1, o2) -> o2.getMatch().compareTo(o1.getMatch());

    private static Comparator<CorgiActivityDetail> timeComparator = Comparator.comparing(CorgiActivity::getSignUpTime);

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        activity.setCheckStatus(AliyunGreenService.PASS);
        activity = aliyunGreenService.checkActivity(activity);
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics(), CheckPic.ACTIVITY);
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        //(activity);
        List<CorgiActivity> corgiActivities = corgiActivityService.getSimilarActivity(activity);
        List<CorgiActivityDetail> details = convertDetail(corgiActivities, activity.getUserId());
        long count = corgiActivityService.countUserActivity(activity.getUserId());
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
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(Arrays.asList(pic1, pic2), CheckPic.ACTIVITY);
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        activity.setTitle("测试34");
        corgiActivityService.updateCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @PostMapping("update_activity")
    public JsonResult updateActivity(@RequestBody CorgiActivity activity) {
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

    @GetMapping("delete_activity")
    public JsonResult deleteActivity(@RequestParam("activityId") String activityId) {
        corgiActivityService.deleteCorgiActivity(activityId);
        return new JsonResult();
    }

    @GetMapping("sign_up")
    public JsonResult signUp(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        corgiUserActivityService.signUp(new UserSignUp(userId, activityId));
        //corgiFavorActivityService.addFavor(userId, activityId);
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
        if (!CollectionUtils.isEmpty(corgiActivities)) {
            HashMap extra = new HashMap();
            extra.put("activityId", activityId);
            extra.put("type", PushMessage.SIGN_UP_MESSAGE_TYPE);
            mqService.sendMessage(PushMessage.builder()
                    .sourceUserId(userId)
                    .targetUserId(corgiActivities.get(0).getUserId())
                    .extra(extra)
                    .message(PushMessage.SIGN_UP_MESSAGE)
                    .build());
        }
        return new JsonResult();
    }

    @GetMapping("sign_out")
    public JsonResult signOut(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        corgiUserActivityService.signOut(new UserSignUp(userId, activityId));
        return new JsonResult();
    }

    @GetMapping("invite")
    public JsonResult invite(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
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
    public JsonResult agree(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
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
            return new JsonResult();
        }
        CorgiActivity activity = corgiActivities.get(0);
        CorgiActivityDetail detail = convertDetail(Arrays.asList(activity), userId).get(0);
        List<CorgiActivity> similarActivities = corgiActivityService.getSimilarActivity(activity);
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
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByUserIds(userIds, CorgiActivity.CREATED, page, size);
        return new JsonResult(convertDetail(corgiActivities, userId));
    }

    @GetMapping("refuse")
    public JsonResult refuse(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
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
        List<CorgiActivity> activityList = corgiActivityService.getCorgiActivityByRange(lng, lat, range, activityQuery);
        List<CorgiActivityDetail> detailList = convertDetail(activityList, userId);
        if (ActivityQuery.SORT_MATCH.equals(activityQuery.getSort())) {
            detailList.sort(detailComparator);
        } else if (ActivityQuery.SORT_TIME.equals(activityQuery.getSort())) {
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
        List<CorgiActivity> result = new ArrayList<>();
        if (CorgiActivity.CREATED.equals(status)) {
            result = corgiActivityService.getUserRunningActivity(userId, page, pageSize);
        } else {
            result = corgiActivityService.getActivityByUserIds(Arrays.asList(userId), "", (page - 1) * pageSize, pageSize);
        }
        return new JsonResult(result);
    }

    @GetMapping("/delete_activity_pic")
    public JsonResult deleteUserPic(@RequestParam("picId") String picId) {
        String result = corgiPicService.deleteActivityPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/add_activity_pic")
    public JsonResult addUserPic(@RequestBody ActivityPic activityPic) {
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(Arrays.asList(activityPic), CheckPic.ACTIVITY);
        String result = corgiPicService.addActivityPic(activityPics.get(0));
        activityPic.setPicId(result);
        return new JsonResult(activityPic);
    }

    @GetMapping("add_favor")
    public JsonResult addFavor(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        corgiFavorActivityService.addFavor(userId, activityId);
        return new JsonResult();
    }

    @GetMapping("delete_favor")
    public JsonResult deleteFavor(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
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
        List<CorgiActivityDetail> detailList = convertDetail(activityList, userId);
        mqService.sendTrace(TraceFollow.builder()
                .userId(userId)
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.FAVOR)
                .build());
        return new JsonResult(detailList);
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

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList, String userId) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = sdf.format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            for (CorgiActivity activity : activityList) {
                activity.setCurrentTime(now);
                UserDetail userDetail = new UserDetail();
                if (activity.getUserId() != null) {
                    userDetail = corgiUserService.getUserDetail(activity.getUserId(), null);
                }
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
                detailList.add(new CorgiActivityDetail(activity)
                        .initUserDetail(userDetail)
                        .initMatch(match)
                        .initSize(height, width)
                        .initSignUpStatus(signUp));
            }
        }
        return detailList;
    }

    private void addArea(CorgiActivity corgiActivity) {
        String city = corgiActivity.getCity();
        String adname = corgiActivity.getAdname();
        if (StringUtils.isEmpty(city) || StringUtils.isEmpty(adname)) {
            return;
        }
        if (!StringUtils.isEmpty(corgiActivity.getBusinessArea())) {
            corgiAreaService.addArea(CorgiArea.builder()
                    .city(city).adname(adname).address("商圈")
                    .type(CorgiArea.BUSINESS)
                    .areaName(corgiActivity.getBusinessArea())
                    .build());
        }
    }
}
