package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.*;
import com.corgi.common.JsonResult;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.service.aliyun.AliyunGreenService;
import com.corgi.user.api.CorgiFavorActivityService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiUserMatchService;
import com.corgi.user.entity.UserProfile;
import com.corgi.user.entity.UserSignUp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
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
    @Autowired
    private AliyunGreenService aliyunGreenService;

    private static Comparator<CorgiActivityDetail> detailComparator = (o1, o2) -> o2.getMatch().compareTo(o1.getMatch());

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(activity.getPics());
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        return new JsonResult(activity);
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
        List<ActivityPic> activityPics = new ArrayList<>();
        ActivityPic pic1 = new ActivityPic();
        ActivityPic pic2 = new ActivityPic();
        activityPics.add(pic1);
        activityPics.add(pic2);
        pic1.setDataId("awegaweg");
        pic1.setPicUrl("awjeigaowejg");
        pic1.setResult("pass");
        pic1.setStatus("normal");
        pic2.setDataId("awegrhrshw4wgewa");
        pic2.setPicUrl("awjeawegigaowejg");
        pic2.setResult("cheawegwegaack");
        pic2.setStatus("check");
        activity.setPics(activityPics);
        activity = corgiActivityService.addCorgiActivity(activity);
        activity.setTitle("测试34");
        corgiActivityService.updateCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @PostMapping("update_activity")
    public JsonResult updateActivity(@RequestBody CorgiActivity activity) {
        activity = corgiActivityService.updateCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @GetMapping("delete_activity")
    public JsonResult deleteActivity(String activityId) {
        corgiActivityService.deleteCorgiActivity(activityId);
        return new JsonResult();
    }

    @GetMapping("sign_up")
    public JsonResult signUp(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        corgiUserActivityService.signUp(new UserSignUp(userId, activityId));
        return new JsonResult();
    }

    @GetMapping("agree")
    public JsonResult agree(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        UserSignUp userSignUp = new UserSignUp(userId, activityId);
        userSignUp.setStatus(UserSignUp.AGREE);
        corgiUserActivityService.updateSignUp(userSignUp);
        return new JsonResult();
    }

    @GetMapping("refuse")
    public JsonResult refuse(@RequestParam("userId") String userId, @RequestParam("activityId") String activityId) {
        UserSignUp userSignUp = new UserSignUp(userId, activityId);
        userSignUp.setStatus(UserSignUp.REFUSE);
        corgiUserActivityService.updateSignUp(userSignUp);
        return new JsonResult();
    }

    @GetMapping("get_sign_up_users")
    public JsonResult getSignUpUsers(@RequestParam("activityId") String activityId) {
        List<UserProfile> userProfiles = corgiUserActivityService.getUsers(activityId);
        return new JsonResult(userProfiles);
    }

    @GetMapping("get_range_activity")
    public JsonResult getRangeActivity(@RequestParam("userId") String userId, @RequestParam("lng") double lng, @RequestParam("lat") double lat, @RequestParam("range") double range, @RequestParam(name = "type", required = false) String type) {
        List<CorgiActivity> activityList = corgiActivityService.getCorgiActivityByRange(lng, lat, range, type);
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(activityList)) {
            for (CorgiActivity activity : activityList) {
                double match = corgiUserMatchService.getUserMatch(userId, activity.getUserId());
                detailList.add(new CorgiActivityDetail(activity).initMatch(match));
            }
        }
        detailList.sort(detailComparator);
        return new JsonResult(detailList);
    }

    @GetMapping("get_user_activity")
    public JsonResult getMyRunningActivity(@RequestParam("userId") String userId, @RequestParam("status") String status) {
        List<CorgiActivity> result = new ArrayList<>();
        if (CorgiActivity.CREATED.equals(status)) {
            result = corgiActivityService.getUserRunningActivity(userId);
        } else if (CorgiActivity.ENDED.equals(status)) {
            result = corgiActivityService.getUserEndedActivity(userId);
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
        List<ActivityPic> activityPics = (List<ActivityPic>) aliyunGreenService.checkPic(Arrays.asList(activityPic));
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
        return new JsonResult(activityList);
    }

    @GetMapping("test")
    public JsonResult test() {
        corgiFavorActivityService.addFavor("1", "aaaa");
        corgiFavorActivityService.addFavor("1", "bbbb");
        corgiFavorActivityService.addFavor("1", "cccc");
        corgiFavorActivityService.deleteFavor("1", "cccc");
        log.info("check" + corgiFavorActivityService.countActivity("1", "bbbb"));
        corgiFavorActivityService.getActivity("1", 1, 20);
        corgiActivityService.getUserRunningActivity("1");
        corgiActivityService.getUserRunningActivity("1");
        return new JsonResult();
    }
}
