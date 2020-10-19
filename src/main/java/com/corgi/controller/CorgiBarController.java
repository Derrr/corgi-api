package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.JWTUtils;
import com.corgi.entity.ActivityQuery;
import com.corgi.entity.BarActivityDetail;
import com.corgi.entity.BarLogin;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.exception.PermissionException;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
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
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiVideoService corgiVideoService;
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
    public JsonResult getBarActivity(@RequestParam("barId") String barId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize, @RequestParam(required = false, name = "status") String status) {
        CorgiActivity corgiActivity = new CorgiActivity();
        corgiActivity.setCategory(CorgiActivity.CAT_BUSINESS);
        corgiActivity.setUserId(barId);
        if (!StringUtils.isEmpty(status)) {
            corgiActivity.setStatus(status);
        }
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

    private Map<String, String> getMap(List<HotActivity> hotActivities) {
        Map hotMap = new HashMap();
        for (HotActivity hotActivity : hotActivities) {
            hotMap.put(hotActivity.getActivityId(), hotActivity.getBarId());
        }
        return hotMap;
    }

    @GetMapping("get_hot_activity")
    public JsonResult getHotActivity(@RequestParam("city") String city) {
        List<HotActivity> hotActivities = corgiHotActivityService.getListByCity(city);
        Map<String, String> hotMap = getMap(hotActivities);

        List<String> activityIds = hotActivities.stream().map(HotActivity::getActivityId).collect(Collectors.toList());
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(activityIds);
        List<BarActivityDetail> barActivityDetails = new ArrayList<>();
        Map<String, BarProfile> barProfileMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(corgiActivities)) {
            for (CorgiActivity corgiActivity : corgiActivities) {
                String barId = hotMap.get(corgiActivity.getId());
                corgiActivity.setBarId(barId);
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
            barProfile.setBarId(getUserId());
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

    @GetMapping("get_bar_account_list")
    public JsonResult getBarAccountList(@RequestParam(required = false, name = "status") String status) {
        if (hasUserId()) {
            return new JsonResult();
        }
        List<BarProfile> barProfiles = corgiBarService.getBarAccountList(status);
        return new JsonResult(barProfiles);
    }

    @GetMapping("get_bar")
    public JsonResult getBar(@RequestParam(required = false, name = "barId") String barId) {
        BarProfile barProfiles = corgiBarService.getBarProfile(barId);
        List<UserPic> userPics = corgiPicService.getUserPic(barId);
        List<BarPic> barPics = new ArrayList<>();
        if (userPics != null) {
            for (UserPic userPic : userPics) {
                BarPic barPic = new BarPic();
                barPic.setBarId(barId);
                barPic.setPicId(userPic.getPicId());
                barPic.setPicUrl(userPic.getPicUrl());
                barPics.add(barPic);
            }
        }
        barProfiles.setBarPics(barPics);
        List<UserVideo> videos = corgiVideoService.getVideo(getUserId());
        if (!CollectionUtils.isEmpty(videos)) {
            UserVideo video = videos.get(0);
            barProfiles.setVideo(video.getVideoUrl());
        }
        return new JsonResult(barProfiles);
    }

    @GetMapping("recommend")
    public JsonResult recommand(@RequestParam(required = false, name = "activityId") String activityId) {
        CorgiActivity activity = new CorgiActivity();
        activity.setId(activityId);
        activity.setRecommend("enable");
        corgiActivityService.updateCorgiActivityStatus(activity);
        return new JsonResult();
    }

    @GetMapping("unrecommend")
    public JsonResult unrecommand(@RequestParam(required = false, name = "activityId") String activityId) {
        CorgiActivity activity = new CorgiActivity();
        activity.setId(activityId);
        activity.setRecommend("disable");
        corgiActivityService.updateCorgiActivityStatus(activity);
        return new JsonResult();
    }

    @GetMapping("login")
    public JsonResult login(@RequestParam("account") String account, @RequestParam("password") String password) throws PermissionException {
        BarProfile profile = corgiBarService.getBarByAccount(account, password);
        BarLogin login = new BarLogin();
        if (profile != null && !StringUtils.isEmpty(profile.getBarId())) {
            BeanUtils.copyProperties(profile, login);
            login.setJwt(JWTUtils.createJWT(profile.getBarId(), "1.0.0"));
        } else {
            throw new PermissionException(Constants.PERMISSION_ERROR_CODE, "账号密码错误");
        }
        return new JsonResult(login);
    }

    @PostMapping("set_account")
    public JsonResult setAccount(@RequestParam("bar") BarProfile barProfile) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiBarService.setBarAccount(barProfile.getBarId(), barProfile.getAccount(), barProfile.getPassword());
        return new JsonResult();
    }

    @GetMapping("/delete_bar_pic")
    public JsonResult deleteBarPic(@RequestParam("picId") String picId) {
        String result = corgiPicService.deleteUserPic(picId, getUserId());
        return getJsonResult(result);
    }

    @PostMapping("/update_bar_pic")
    public JsonResult updateBarPic(@RequestBody UserPic userPic) {
        if (hasUserId()) {
            userPic.setUserId(getUserId());
        }
        String result = corgiPicService.updateUserPic(userPic);
        return getJsonResult(result);
    }

    @PostMapping("/add_bar_pic")
    public JsonResult addBarPic(@RequestBody UserPic userPic) {
        if (hasUserId()) {
            userPic.setUserId(getUserId());
        }
        String result = corgiPicService.addUserPic(userPic);
        userPic.setPicId(result);
        return new JsonResult(userPic);
    }

    @GetMapping("/delete_bar_video")
    public JsonResult deleteBarVideo() {
        corgiVideoService.deleteVideo(getUserId());
        return new JsonResult();
    }

    @PostMapping("/add_bar_video")
    public JsonResult addBarVideo(@RequestBody UserVideo userVideo) {
        if (hasUserId()) {
            userVideo.setUserId(getUserId());
        }
        corgiVideoService.deleteVideo(userVideo.getUserId());
        corgiVideoService.addVideo(userVideo);
        return new JsonResult(userVideo);
    }

}

