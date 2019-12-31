package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.CharacterUtils;
import com.corgi.entity.StorageToken;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.*;
import io.lettuce.core.dynamic.annotation.Param;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("tool")
public class CorgiToolController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;

    @Reference
    private CorgiActivityService corgiActivityService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("query_user")
    public JsonResult queryUser(UserDetail userDetail) {
        List<UserProfile> profiles = corgiUserService.searchUsers(userDetail);
        return new JsonResult(profiles);
    }

    @GetMapping("query_activity")
    public JsonResult queryActivity(CorgiActivity activity) {
        List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(activity);
        return new JsonResult(activityList);
    }

}
