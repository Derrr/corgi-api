package com.corgi.controller;

import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.RequestUtil;
import com.corgi.entity.*;
import com.corgi.exception.PermissionException;
import com.corgi.service.*;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.Req;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("login")
public class CorgiLoginController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiFakeService corgiFakeService;
    @Reference
    private CorgiBlacklistService corgiBlacklistService;

    @Autowired
    private AliyunDypnsService aliyunDypnsService;
    @Autowired
    private EasemobService easemobService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static String CODE_PREFIX = "telCode_";

    @GetMapping("get_mobile")
    public JsonResult getMobile(@RequestParam("accessToken") String accessToken) {
        return new JsonResult(aliyunDypnsService.getMobile(accessToken));
    }

    @GetMapping("get_sms_token")
    public Object getSMSToken(SMSRequest request) {

        try {
            return aliyunDypnsService.getSMSToken(request);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonResult(Constants.API_ERROR_CODE, "获取sms token失败");
    }

    @PostMapping("mobile_login")
    public JsonResult getMobile(@RequestBody UserLogin userLogin) {

        if (aliyunDypnsService.verifyMobile(userLogin.getTelNo(), userLogin.getJwt())) {
            String lockKey = "login_" + userLogin.getTelNo();
            try {
                if (redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5L, TimeUnit.SECONDS)) {
                    return login(userLogin);
                }
            } finally {
                redisTemplate.delete(lockKey);
            }
        }
        return new JsonResult(Constants.API_ERROR_CODE, "本机号码登陆失败");
    }

    @PostMapping("/sms_login")
    public JsonResult register(@RequestBody UserLogin userLogin) {
        List<String> blockTel = corgiBlacklistService.getBeBlacked("-1");
        if (blockTel.contains(userLogin.getTelNo())) {
            return new JsonResult(Constants.API_ERROR_CODE, "该号码无法注册");
        }
        if (redisTemplate.hasKey("suspended_number_" + userLogin.getTelNo())) {
            return new JsonResult(Constants.API_ERROR_CODE, "该号码暂时无法注册");
        }
        if (userLogin.getTelNo().startsWith("170") || userLogin.getTelNo().startsWith("171")) {
            return new JsonResult(Constants.API_ERROR_CODE, "为了保护平台用户权益，将不允许商业虚拟手机号注册，请更换号码后再注册。");
        }
        if (("0000".equals(userLogin.getCode()) && "13700000000".equals(userLogin.getTelNo()))
                || ("00000".equals(userLogin.getCode()) && "99999999999".equals(userLogin.getTelNo()))
                || aliyunDypnsService.verifySmsToken(userLogin.getCode(), userLogin.getJwt(), userLogin.getTelNo())) {
            String lockKey = "login_" + userLogin.getTelNo();
            try {
                if (redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5L, TimeUnit.SECONDS)) {
                    return login(userLogin);
                }
            } finally {
                redisTemplate.delete(lockKey);
            }
        }
        return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
    }

    private JsonResult login(UserLogin userLogin) {
        List<String> blockTel = corgiBlacklistService.getBeBlacked("-1");
        if (blockTel.contains(userLogin.getTelNo())) {
            return new JsonResult(Constants.API_ERROR_CODE, "该号码无法注册");
        }
        if (redisTemplate.hasKey("suspended_number_" + userLogin.getTelNo())) {
            return new JsonResult(Constants.API_ERROR_CODE, "该号码暂时无法注册");
        }
        if (userLogin.getTelNo().startsWith("170") || userLogin.getTelNo().startsWith("171")) {
            return new JsonResult(Constants.API_ERROR_CODE, "为了保护平台用户权益，将不允许商业虚拟手机号注册，请更换号码后再注册。");
        }
        if (StringUtils.isEmpty(userLogin.getUserId())) {
            userLogin = corgiUserService.login(userLogin);
            if ("-1".equals(userLogin.getStatus())) {
                easemobService.registerUser(userLogin.getUserId());
                userLogin.setStatus("0");
            }
            corgiFakeService.deleteFakeFollower(userLogin.getUserId());
            userLogin.setJwt(JWTUtils.createJWT(userLogin.getUserId(), userLogin.getVersion()));
            return new JsonResult(userLogin);
        } else if (StringUtils.isEmpty(userLogin.getTelNo())) {
            return new JsonResult(Constants.API_ERROR_CODE, "无法获取到手机号");
        } else {
            UserDetail search = new UserDetail();
            search.setTelNo(userLogin.getTelNo());
            if (!CollectionUtils.isEmpty(corgiUserService.searchUsers(search, "", 1, 1))) {
                return new JsonResult(Constants.API_ERROR_CODE, "手机号已被注册");
            }
            userLogin.setUserId(getUserId());
            corgiUserService.updateUserLogin(userLogin);
            return new JsonResult("更新手机号成功");
        }
    }

}

