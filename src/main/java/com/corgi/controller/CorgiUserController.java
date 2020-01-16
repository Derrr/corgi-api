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
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.CharacterUtils;
import com.corgi.entity.CheckPic;
import com.corgi.entity.StorageToken;
import com.corgi.service.aliyun.AliyunGreenService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.*;
import io.lettuce.core.dynamic.annotation.Param;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("user")
public class CorgiUserController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;

    @Reference
    private CorgiPicService corgiPicService;

    @Reference
    private CorgiUserFollowService corgiUserFollowService;

    @Reference
    private CorgiToolService corgiToolService;

    @Autowired
    private AliyunGreenService aliyunGreenService;

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

    private static String CODE_PREFIX = "telCode_";

    @PostMapping("/login")
    public JsonResult register(@RequestBody UserLogin userLogin) {
        String code = redisTemplate.opsForValue().get(CODE_PREFIX + userLogin.getTelNo());
        if ((code != null && code.equals(userLogin.getCode())) || "00000".equals(userLogin.getCode())) {
            userLogin = corgiUserService.login(userLogin);
            return new JsonResult(userLogin);
        }
        return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
    }

    @PostMapping("/add_user")
    public JsonResult addUser(@RequestBody UserDetail userDetail) {
        List<UserPic> pics = (List<UserPic>) aliyunGreenService.checkPic(userDetail.getUserPics(), CheckPic.USER);
        userDetail.setUserPics(pics);
        userDetail = aliyunGreenService.checkAvatar(userDetail);
        String result = corgiUserService.addDetail(userDetail);
        return getJsonResult(result);
    }

    @PostMapping("/update_user")
    public JsonResult updateUser(@RequestBody UserDetail userDetail) {
        userDetail = aliyunGreenService.checkAvatar(userDetail);
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
        String result = corgiPicService.deleteUserPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/update_user_pic")
    public JsonResult updateUserPic(@RequestBody UserPic userPic) {
        List<UserPic> userPics = (List<UserPic>) aliyunGreenService.checkPic(Arrays.asList(userPic), CheckPic.USER);
        String result = corgiPicService.updateUserPic(userPics.get(0));
        return getJsonResult(result);
    }

    @PostMapping("/add_user_pic")
    public JsonResult addUserPic(@RequestBody UserPic userPic) {
        List<UserPic> userPics = (List<UserPic>) aliyunGreenService.checkPic(Arrays.asList(userPic), CheckPic.USER);
        String result = corgiPicService.addUserPic(userPics.get(0));
        userPic.setPicId(result);
        return new JsonResult(userPic);
    }

    @GetMapping("/get_user_detail")
    public JsonResult getUserDetail(@RequestParam("userId") String userId) {
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

    @GetMapping("/get_nearby_user")
    public JsonResult getNearbyUser(UserQuery userQuery) {
        UserPosition userPosition = new UserPosition();
        userPosition.setUserId(userQuery.getUserId());
        userPosition.setLat(userQuery.getLat());
        userPosition.setLng(userQuery.getLng());

        corgiUserService.updateUserPosition(userPosition);
        List<UserProfile> userProfiles = corgiUserService.getNearByUser(userQuery);
        return new JsonResult(userProfiles);
    }

    @GetMapping("/send_code")
    public JsonResult sendToken(@RequestParam("telNo") String telNo) {
        Random random = new Random();
        String code = "";
        for (int i = 0; i < 4; i++) {
            code += random.nextInt(10);
        }
        redisTemplate.opsForValue().set(CODE_PREFIX + telNo, code, 5, TimeUnit.MINUTES);
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");
        request.putQueryParameter("RegionId", "cn-hangzhou");
        request.putQueryParameter("PhoneNumbers", telNo);
        request.putQueryParameter("SignName", "Corgi");
        request.putQueryParameter("TemplateCode", "SMS_180049529");
        request.putQueryParameter("TemplateParam", "{\"code\":\"" + code + "\"}");
        try {
            CommonResponse response = client.getCommonResponse(request);
            log.info(response.getData());
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return new JsonResult();
    }

    @GetMapping("get_user_questions")
    public JsonResult getUserQuestions() {
        return new JsonResult(CharacterUtils.getUserQuestions());
    }

    @GetMapping("follow")
    public JsonResult follow(@Param("userId") String userId, @Param("targetUserId") String targetUserId) {
        corgiUserFollowService.follow(userId, targetUserId);
        return new JsonResult();
    }

    @GetMapping("unfollow")
    public JsonResult unfollow(@Param("userId") String userId, @Param("targetUserId") String targetUserId) {
        corgiUserFollowService.unfollow(userId, targetUserId);
        return new JsonResult();
    }

    @GetMapping("is_followed")
    public JsonResult isFollowed(@Param("userId") String userId, @Param("targetUserId") String targetUserId) {
        int result = corgiUserFollowService.isFollowed(userId, targetUserId);
        return new JsonResult(result);
    }


    @GetMapping("update_user_tag")
    public JsonResult updateUserTag(@RequestParam("userId") String userId, @RequestParam("tags") List<String> tags) {
        corgiToolService.updateUserTag(userId, tags);
        return new JsonResult();
    }

    @GetMapping("update_user_interest")
    public JsonResult updateUserInterest(@RequestParam("userId") String userId, @RequestParam("category") String category, @RequestParam("interests") List<String> interests) {
        corgiToolService.updateUserInterest(userId, category, interests);
        return new JsonResult();
    }

    @GetMapping("test")
    public JsonResult test() {
        UserDetail userDetail = new UserDetail();
        userDetail.setNickname("testtestsss");
        userDetail.setAvatar("http://www.aeeddd.com");
        userDetail = aliyunGreenService.checkAvatar(userDetail);
        corgiUserService.addDetail(userDetail);
        return new JsonResult(corgiUserService.getUserDetail("1"));
    }

}
