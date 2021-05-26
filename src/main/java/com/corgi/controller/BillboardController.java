package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.entity.ActivityBillboard;
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
import java.util.HashMap;
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

    @GetMapping("update_order")
    public JsonResult updateOrder(@RequestParam("userId") String userId, @RequestParam("date") String date, @RequestParam("order")Integer order) {
        corgiBillboardService.updateBillboardOrder(userId, date, order);
        return new JsonResult();
    }

    @GetMapping("get_activity_billboard")
    public JsonResult getActivityBillboard() {
        List<String> activityIds = corgiBillboardService.getActivityBillboard();
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        return new JsonResult(buildActivityBillboard(activities, true));
    }

    @GetMapping("get_all_activity_billboard")
    public JsonResult getAllActivityBillboard() {
        List<ActivityBillboard> activityBoards = corgiBillboardService.getAllActivityBillboard();
        List<String> activityIds = new ArrayList<>();
        HashMap<String, String> lastTimeMap = new HashMap<>();
        for (ActivityBillboard billboard : activityBoards) {
            activityIds.add(billboard.getActivityId());
            lastTimeMap.put(billboard.getActivityId(), billboard.getLastTime());
        }
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<ActivityBillboardDetail> activityBillboardDetails = buildActivityBillboard(activities, true);
        for (ActivityBillboardDetail detail : activityBillboardDetails) {
            detail.setLastTime(lastTimeMap.get(detail.getId()));
        }
        return new JsonResult(activityBillboardDetails);
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

    private List<ActivityBillboardDetail> buildActivityBillboard(List<CorgiActivity> activities, boolean all) {
        List<ActivityBillboardDetail> billboards = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        String nowTime = sdf.format(new Date());
        for (CorgiActivity corgiActivity : activities) {
            if (corgiActivity == null || StringUtils.isEmpty(corgiActivity.getId())) {
                continue;
            }
            ActivityBillboardDetail billboard = ActivityBillboardDetail.getResult(corgiActivity);
            if (!all && !StringUtils.isEmpty(billboard.getSignUpTime()) && nowTime.compareTo(billboard.getSignUpTime()) > 0) {
                continue;
            }
            if (!all && CorgiActivity.DELETED.equals(billboard.getStatus())) {
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
            billboards.add(billboard);
        }
        return billboards;
    }

}
