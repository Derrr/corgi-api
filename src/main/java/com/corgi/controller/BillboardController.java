package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboard;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("billboard")
public class BillboardController extends BaseController {
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiActivityService corgiActivityService;

    @GetMapping("get_by_date")
    public JsonResult getByDate(@RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate) {
        return new JsonResult(corgiBillboardService.getBillboardByDate(startDate, endDate));
    }

    @GetMapping("update_by_nickname")
    public JsonResult updateByNickname(@RequestParam("from") String from, @RequestParam("to") String to, @RequestParam("date") String date) {
        corgiBillboardService.updateBillboardByNickname(from, to, date);
        return new JsonResult();
    }

    @GetMapping("get_activity_billboard")
    public JsonResult getActivityBillboard() {
        List<String> activityIds = corgiBillboardService.getActivityBillboard();
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        return new JsonResult(buildActivityBillboard(activities));
    }

    @GetMapping("add_activity_billboard")
    public JsonResult addActivityBillboard(@RequestParam("activityId") String activityId) {
        if (!hasUserId()) {
            corgiBillboardService.addActivityBillboard(activityId);
        }
        return new JsonResult();
    }

    @GetMapping("delete_activity_billboard")
    public JsonResult deleteActivityBillboard(@RequestParam("activityId") String activityId) {
        if (!hasUserId()) {
            corgiBillboardService.deleteActivityBillboard(activityId);
        }
        return new JsonResult();
    }

    private List<ActivityBillboard> buildActivityBillboard(List<CorgiActivity> activities) {
        List<ActivityBillboard> billboards = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        String nowTime = sdf.format(new Date());
        for (CorgiActivity corgiActivity : activities) {
            if (corgiActivity == null || StringUtils.isEmpty(corgiActivity.getId())) {
                continue;
            }
            ActivityBillboard billboard = ActivityBillboard.getResult(corgiActivity);
            if (!StringUtils.isEmpty(billboard.getSignUpTime()) && nowTime.compareTo(billboard.getSignUpTime()) > 0) {
                continue;
            }
            billboard.setPics(corgiPicService.getActivityPic(billboard.getId()));
            List<UserProfile> userProfiles = corgiUserActivityService.getUsers(billboard.getId(), null, null);
            List<UserProfile> signUpUsers = new ArrayList<>();
            int i = 0;
            for (UserProfile profile : userProfiles) {
                if (i >= 3) {
                    break;
                }
                signUpUsers.add(profile);
            }
            billboard.setSignUpUsers(signUpUsers);
            billboard.setSignUpCount(userProfiles.size());
        }
        return billboards;
    }

}
