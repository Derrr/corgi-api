package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.*;
import com.corgi.common.JsonResult;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiUserMatchService;
import com.corgi.user.entity.UserProfile;
import com.corgi.user.entity.UserSignUp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    private static Comparator<CorgiActivityDetail> detailComparator = (o1, o2) -> o2.getMatch().compareTo(o1.getMatch());

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        activity = corgiActivityService.addCorgiActivity(activity);
        return new JsonResult(activity);
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
    public JsonResult getRangeActivity(@RequestParam("userId") String userId, @RequestParam("lng") double lng, @RequestParam("lat") double lat, @RequestParam("range") double range) {
        List<CorgiActivity> activityList = corgiActivityService.getCorgiActivityByRange(lng, lat, range);
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
}
