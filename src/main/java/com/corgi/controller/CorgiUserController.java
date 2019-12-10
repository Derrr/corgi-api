package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.CharacterUtils;
import com.corgi.entity.StorageToken;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("user")
public class CorgiUserController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${aliyun.bucketName}")
    private String bucketName;

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;

    @Value("${aliyun.sts.endpoint}")
    private String stsEndpoint;

    @Value("${aliyun.endpoint}")
    private String endpoint;

    @Value("${aliyun.role.arn}")
    private String roleArn;


    @PostMapping("/login")
    public JsonResult register(@RequestBody UserLogin userLogin) {
        if ("000000".equals(userLogin.getCode())) {
            userLogin = corgiUserService.login(userLogin);
            return new JsonResult(userLogin);
        }
        return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
    }

    @PostMapping("/add_user")
    public JsonResult addUser(@RequestBody UserDetail userDetail) {
        String result = corgiUserService.addDetail(userDetail);
        return getJsonResult(result);
    }

    @PostMapping("/update_user")
    public JsonResult updateUser(@RequestBody UserDetail userDetail) {
        String result = corgiUserService.updateDetail(userDetail);
        return getJsonResult(result);
    }

    @PostMapping("/update_prefer_group")
    public JsonResult updatePreferGroup(@RequestBody UserDetail userDetail) {
        String result = corgiUserService.updatePreferGroup(userDetail.getUserId(), userDetail.getPreferGroup());
        return getJsonResult(result);
    }

    @GetMapping("/delete_user_pic")
    public JsonResult deleteUserPic(@RequestParam("picId") String picId) {
        String result = corgiUserService.deleteUserPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/add_user_pic")
    public JsonResult addUserPic(@RequestBody UserPic userPic) {
        String result = corgiUserService.addUserPic(userPic);
        userPic.setPicId(result);
        return new JsonResult(userPic);
    }

    @GetMapping("/get_user_detail")
    public JsonResult getUserDetail(@RequestParam("user_id") String userId) {
        UserDetail userDetail = corgiUserService.getUserDetail(userId);
        return new JsonResult(userDetail);
    }


    @GetMapping("/get_upload_token")
    public JsonResult getUploadToken() {
        try {
            // 添加endpoint（直接使用STS endpoint，前两个参数留空，无需添加region ID）
            DefaultProfile.addEndpoint("", "", "Sts", stsEndpoint);
            // 构造default profile（参数留空，无需添加region ID）
            IClientProfile profile = DefaultProfile.getProfile("", accessKeyId, accessKeySecret);
            // 用profile构造client
            DefaultAcsClient client = new DefaultAcsClient(profile);
            final AssumeRoleRequest request = new AssumeRoleRequest();
            request.setMethod(MethodType.POST);
            request.setRoleArn(roleArn);
            request.setRoleSessionName("corgiManager");
            request.setDurationSeconds(3600L);
            final AssumeRoleResponse response = client.getAcsResponse(request);

            StorageToken token = new StorageToken();
            token.setBucketName(bucketName);
            token.setEndpoint(endpoint);
            token.setSecurityToken(response.getCredentials().getSecurityToken());
            token.setAccessKeyId(response.getCredentials().getAccessKeyId());
            token.setAccessKeySecret(response.getCredentials().getAccessKeySecret());
            token.setExpiration(response.getCredentials().getExpiration());
            return new JsonResult(token);
        } catch (ClientException e) {
            log.error(e.getMessage(), e);
            return new JsonResult(Constants.SYS_ERROR_CODE, e.getErrMsg());
        }
    }

    @PostMapping("/update_user_position")
    public JsonResult updateUserPosition(@RequestBody UserPosition userPosition) {
        corgiUserService.updateUserPosition(userPosition);
        return new JsonResult();
    }

    @PostMapping("/get_nearby_user")
    public JsonResult getNearbyUser(@RequestParam("userId") String userId, @RequestParam("lat") Double lat, @RequestParam("lng") Double lng, @RequestParam("range") Double range) {
        UserPosition userPosition = new UserPosition();
        userPosition.setUserId(userId);
        userPosition.setLat(lat);
        userPosition.setLng(lng);

        corgiUserService.updateUserPosition(userPosition);
        List<UserProfile> userProfiles = corgiUserService.getNearByUser(userPosition, range);
        return new JsonResult(userProfiles);
    }

    @GetMapping("/send_code")
    public JsonResult sendToken(@RequestParam("telNo") String telNo) {
        return null;
    }

    @GetMapping("get_user_questions")
    public JsonResult getUserQuestions(){
        return new JsonResult(CharacterUtils.getUserQuestions());
    }

}
