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
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserLogin;
import com.corgi.user.entity.UserPic;
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
    public JsonResult deleteUserPic(@RequestParam("pic_id") String picId) {
        String result = corgiUserService.deleteUserPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/add_user_pic")
    public JsonResult addUserPic(@RequestBody UserPic userPic) {
        String result = corgiUserService.addUserPic(userPic);
        return getJsonResult(result);
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
            DefaultProfile.addEndpoint("", "", "Sts", endpoint);
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
            return new JsonResult(response.getCredentials());
        } catch (ClientException e) {
            return new JsonResult(500, e.getErrMsg());
        }
    }

}
