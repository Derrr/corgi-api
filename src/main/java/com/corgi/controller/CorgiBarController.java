package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.entity.ActivityQuery;
import com.corgi.entity.BarActivityDetail;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.exception.PermissionException;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.CorgiAreaService;
import com.corgi.user.api.CorgiBarService;
import com.corgi.user.api.CorgiHotActivityService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.BarProfile;
import com.corgi.user.entity.HotActivity;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("bar")
public class CorgiBarController extends BaseController {
    @Reference
    private CorgiBarService corgiBarService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiHotActivityService corgiHotActivityService;
    @Autowired
    private CorgiUtilService corgiUtilService;


    @PostMapping("add_bar_activity")
    public JsonResult addBarActivity(@RequestBody CorgiActivity corgiActivity) {
        if (hasUserId()) {
            corgiActivity.setUserId(getUserId());
        }
        corgiActivity.setCategory(CorgiActivity.CAT_BUSINESS);
        CorgiActivity activity = corgiActivityService.addCorgiActivity(corgiActivity);
        return new JsonResult(activity);
    }

    @GetMapping("get_bar_activity")
    public JsonResult getBarActivity(@RequestParam("barId") String barId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiActivity corgiActivity = new CorgiActivity();
        corgiActivity.setCategory(CorgiActivity.CAT_BUSINESS);
        corgiActivity.setUserId(barId);
        List<CorgiActivity> corgiActivities = corgiActivityService.searchCorgiActivity(corgiActivity, page, pageSize);
        return new JsonResult(corgiActivities);
    }

    @GetMapping("get_active_bar_activity")
    public JsonResult getActiveBarActivity(@RequestParam("barId") String barId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        Date nowDate = new Date();
        CorgiActivity corgiActivity = new CorgiActivity();
        corgiActivity.setCategory(CorgiActivity.CAT_BUSINESS);
        corgiActivity.setStatus(CorgiActivity.CREATED);
        corgiActivity.setUserId(barId);
        corgiActivity.setEndTime(sdf.format(nowDate));
        List<CorgiActivityDetail> corgiActivities = corgiUtilService.convertUserActivityDetail(corgiActivityService.getBarActivity(corgiActivity), getUserId());
        return new JsonResult(corgiActivities);
    }

    @PostMapping("update_bar_activity")
    public JsonResult updateActivity(@RequestBody CorgiActivity activity) throws PermissionException {
        if (hasUserId()) {
            throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
        }
        activity.setCategory(CorgiActivity.CAT_BUSINESS);
        activity = corgiActivityService.updateCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @GetMapping("get_hot_activity")
    public JsonResult getHotActivity(@RequestParam("city") String city) {
        List<HotActivity> hotActivities = corgiHotActivityService.getListByCity(city);
        List<String> activityIds = hotActivities.stream().map(HotActivity::getActivityId).collect(Collectors.toList());
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(activityIds);
        List<BarActivityDetail> barActivityDetails = new ArrayList<>();
        Map<String, BarProfile> barProfileMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(corgiActivities)) {
            for (CorgiActivity corgiActivity : corgiActivities) {
                String barId = corgiActivity.getBarId();
                BarActivityDetail detail = new BarActivityDetail(corgiActivity);
                if (barProfileMap.get(barId) != null) {
                    detail.setBarDetail(barProfileMap.get(barId));
                    barActivityDetails.add(detail);
                    continue;
                }
                BarProfile barProfile = corgiBarService.getBarProfile(barId);
                barProfileMap.put(barId, barProfile);
                detail.setBarDetail(barProfile);
                barActivityDetails.add(detail);
            }
        }
        return new JsonResult(barActivityDetails);
    }

    @PostMapping("add_bar")
    public JsonResult addBar(@RequestBody BarProfile barProfile) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiBarService.addBarProfile(barProfile);
        return new JsonResult();
    }

    @PostMapping("update_bar")
    public JsonResult updateBar(@RequestBody BarProfile barProfile) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiBarService.updateBarProfile(barProfile);
        return new JsonResult();
    }

    @GetMapping("search_bar")
    public JsonResult search(BarProfile barProfile) {
        barProfile.setStatus(BarProfile.STATUS_ENABLE);
        if (barProfile.getAddress() == null) {
            barProfile.setAddress(barProfile.getBarName());
        }
        List<BarProfile> barProfiles = corgiBarService.searchBar(barProfile);
        return new JsonResult(barProfiles);
    }

    @GetMapping("get_bar_list")
    public JsonResult getBarList(@RequestParam(required = false, name = "status") String status) {
        List<BarProfile> barProfiles = corgiBarService.getBarList(status);
        return new JsonResult(barProfiles);
    }

    @GetMapping("get_bar")
    public JsonResult getBar(@RequestParam(required = false, name = "barId") String barId) {
        BarProfile barProfiles = corgiBarService.getBarProfile(barId);
        return new JsonResult(barProfiles);
    }

}

