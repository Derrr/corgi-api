package com.corgi.controller;

import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.BarActivityDetail;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.exception.PermissionException;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.MQService;
import com.corgi.user.api.CorgiBarService;
import com.corgi.user.api.CorgiHotActivityService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.BarProfile;
import com.corgi.user.entity.CorgiUserGoods;
import com.corgi.user.entity.HotActivity;
import com.corgi.user.entity.UserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("hot")
public class CorgiHotController extends BaseController {
    @Reference
    private CorgiHotActivityService corgiHotActivityService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiPicService corgiPicService;
    @Autowired
    private MQService mqService;

    @PostMapping("add_hot_activity")
    public JsonResult addHotActivity(@RequestBody HotActivity hotActivity) {
        if (!hasUserId()) {
            corgiHotActivityService.addHotActivity(hotActivity);
        }
        return new JsonResult();
    }

    @GetMapping("list_hot_activity")
    public JsonResult listHotActivity() {
        List<HotActivity> corgiActivities = new ArrayList<>();
        if (!hasUserId()) {
            corgiActivities = corgiHotActivityService.getHotActivityList();
        }
        return new JsonResult(corgiActivities);
    }

    @PostMapping("update_hot_activity")
    public JsonResult updateActivity(@RequestBody HotActivity activity) throws PermissionException {
        if (hasUserId()) {
            throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
        }
        corgiHotActivityService.updateHotActivity(activity);
        return new JsonResult();
    }

    @GetMapping("delete_hot_activity")
    public JsonResult getHotActivity(@RequestParam("hotId") String hotId) throws PermissionException {
        if (hasUserId()) {
            throw new PermissionException(Constants.API_ERROR_CODE, "无权限操作");
        }
        corgiHotActivityService.deleteHotActivity(hotId);
        return new JsonResult();
    }

    @GetMapping("search_hot_activity")
    public JsonResult search(HotActivity hotActivity) {
        List<HotActivity> hotActivities = corgiHotActivityService.searchHotActivity(hotActivity);
        return new JsonResult(hotActivities);
    }

}

