package com.corgi.controller;

import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.cloudauth.model.v20190307.CompareFacesResponse;
import com.aliyuncs.cloudauth.model.v20190307.DescribeVerifyResultResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.api.CorgiMatchService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.util.CharacterUtils;
import com.corgi.common.util.IPUtil;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.RequestUtil;
import com.corgi.entity.*;
import com.corgi.exception.PermissionException;
import com.corgi.service.*;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("user")
public class CorgiUserController extends BaseController {
    @Reference
    private CorgiBlacklistService corgiBlacklistService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserTestService corgiUserTestService;
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiSoundService corgiSoundService;
    @Reference
    private CorgiVisitService corgiVisitService;
    @Reference
    private CorgiUserDateService corgiUserDateService;
    @Reference
    private CorgiFakeService corgiFakeService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiShareService corgiShareService;
    @Reference
    private CorgiMatchService corgiMatchService;

    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private EasemobService easemobService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;
    @Autowired
    private MailService mailService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private AsyncTaskService asyncTaskService;

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
    public static final String CALL_USER_CITY_PREFIX = "call_user_city_";
    public static final List<String> AVATRS = Arrays.asList("https://corgi-pic.oss-cn-beijing.aliyuncs.com/default-avatar/corgi-butt.png",
            "https://corgi-pic.oss-cn-beijing.aliyuncs.com/default-avatar/corgi-front.png",
            "https://corgi-pic.oss-cn-beijing.aliyuncs.com/default-avatar/corgi-profile.png");

    @PostMapping("/login")
    public JsonResult register(@RequestBody UserLogin userLogin) {
        String lockKey = "login_" + userLogin.getTelNo();
        String code = redisTemplate.opsForValue().get(CODE_PREFIX + userLogin.getTelNo());
        if ((code != null && code.equals(userLogin.getCode()))
                || ("0000".equals(userLogin.getCode()) && "13700000000".equals(userLogin.getTelNo()))
                || ("00000".equals(userLogin.getCode()) && "99999999999".equals(userLogin.getTelNo()))) {
            try {
                corgiUtilService.lock(lockKey);
                if (StringUtils.isEmpty(userLogin.getUserId())) {
                    UserDetail search = new UserDetail();
                    search.setTelNo(userLogin.getTelNo());
                    if (!"13700000000".equals(userLogin.getTelNo()) && CollectionUtils.isEmpty(corgiUserService.searchUsers(search, "", 1, 1))) {
                        if (aliyunGreenService.checkAccount(userLogin.getTelNo()) > 65.0) {
                            return new JsonResult(Constants.API_ERROR_CODE, "为了保护平台用户权益，将不允许高风险手机号注册，请更换号码后再注册。");
                        }
                    }
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
            } finally {
                corgiUtilService.unlock(lockKey);
            }
        }
        return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
    }

    @GetMapping("/share")
    public JsonResult shareUser(@RequestParam("userId") String userId) {
        asyncTaskService.initRecommendUser(userId);
        ActivityShare share = new ActivityShare();
        share.setUserId(userId);
        share.setShareUserId(getUserId());
        share.setActivityId("0");
        corgiShareService.addShare(share);
        return new JsonResult();
    }

    @GetMapping("/unregister")
    public JsonResult unregister() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String time = sdf.format(new Date());
        UserLogin userLogin = new UserLogin();
        userLogin.setUserId(getUserId());
        userLogin.setUnregisterDate(time);
        corgiUserService.updatePush(userLogin);
        return new JsonResult(time);
    }

    @GetMapping("/cancel_unregister")
    public JsonResult cancelUnregister() {
        UserLogin userLogin = new UserLogin();
        userLogin.setUserId(getUserId());
        userLogin.setUnregisterDate("0");
        corgiUserService.updatePush(userLogin);
        return new JsonResult();
    }

    @PostMapping("/login_test")
    public JsonResult registerTest(@RequestBody UserLogin userLogin) {
//        String code = redisTemplate.opsForValue().get(CODE_PREFIX + userLogin.getTelNo());
//        if ((code != null && code.equals(userLogin.getCode())) || "00000".equals(userLogin.getCode()) || "13700000000".equals(userLogin.getTelNo())) {
        //if (StringUtils.isEmpty(userLogin.getUserId())) {
        //userLogin = corgiUserTestService.login(userLogin);
        if ("-1".equals(userLogin.getStatus())) {
            easemobService.registerUser(userLogin.getUserId());
            userLogin.setStatus("0");
        }
        userLogin.setJwt(JWTUtils.createJWT(userLogin.getUserId(), userLogin.getVersion()));
        return new JsonResult(userLogin);
//            } else if (StringUtils.isEmpty(userLogin.getTelNo()) || StringUtils.isEmpty(userLogin.getImId())) {
//                return new JsonResult(Constants.API_ERROR_CODE, "无法获取到手机号/推送ID");
//            } else {
//                corgiUserTestService.updateUserLogin(userLogin);
//                return new JsonResult("更新手机号成功");
//            }
        //}
        //return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
    }

    @PostMapping("/update_push")
    public JsonResult updatePush(@RequestBody UserLogin userLogin) {
        if (hasUserId()) {
            userLogin.setUserId(getUserId());
        }
        corgiUserService.updatePush(userLogin);
        return new JsonResult();
    }

    @GetMapping("/get_basic_detail")
    public JsonResult getBasicDetail(@RequestParam("userId") String userId) {
        UserLogin userLogin = corgiUserService.getUserLogin(userId);
        if (userLogin == null || StringUtils.isEmpty(userLogin.getUserId())) {
            return new JsonResult(Constants.PERMISSION_ERROR_CODE, "用户不存在");
        }
        return new JsonResult(userLogin);
    }

    @PostMapping("/add_user")
    public JsonResult addUser(@RequestBody UserDetail userDetail) {
        if (hasUserId()) {
            userDetail.setUserId(getUserId());
        }
        String key = "add_user-" + getUserId();
        if (!redisTemplate.opsForValue().setIfAbsent(key, System.currentTimeMillis() + "", 5l, TimeUnit.SECONDS)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "更新太频繁");
        }
//        if (StringUtils.isEmpty(userDetail.getBirthday())) {
//            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "请填写你的生日");
//        }
//        int count = corgiUserService.countUserNickname(userDetail.getNickname());
//        if (count > 0) {
//            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
//        }
        List<UserPic> pics = (List<UserPic>) aliyunGreenService.checkPic(userDetail.getUserPics(), userDetail.getUserId(), CheckPic.USER);
        if (pics == null) {
            pics = new ArrayList<>();
        }
        if (!userDetail.getAvatar().contains("defaultAvatar")) {
            userDetail = aliyunGreenService.checkAvatar(userDetail);
        } else {
            userDetail.setAvatarCheckStatus("default");
        }
        if (pics.size() == 0) {
            UserPic userPic = new UserPic();
            userPic.setPicUrl(userDetail.getAvatar());
            userPic.setStatus(userDetail.getAvatarCheckStatus());
            userPic.setDataId(userDetail.getAvatarDataId());
            pics.add(userPic);
        }
        userDetail.setUserPics(pics);
        userDetail.setCheckStatus(AliyunGreenService.PASS);
        if (!aliyunGreenService.checkText(userDetail.getNickname()).isPass()) {
            userDetail.setCheckStatus(AliyunGreenService.CHECK);
            mailService.sendCheckMessage("用户：", userDetail.getUserId());
        }
        userDetail = aliyunGreenService.checkDesc(userDetail);
        userDetail.setGroup(changeGroup(userDetail.getGroup()));
        userDetail.setPreferGroup(changeGroupList(userDetail.getPreferGroup()));
        userDetail.setBackground(CheckPic.getDefaultBackground());
        userDetail.setBgCheckStatus(AliyunGreenService.PASS);
        userDetail.setBgDataId("-");
        String result = corgiUserService.addDetail(userDetail);
        mqService.sendRegisterMessage(PushMessage.builder()
                .targetUserId(userDetail.getUserId()).build());
        return getJsonResult(result);
    }

    @PostMapping("/add_user_test")
    public JsonResult addUserTest(@RequestBody UserDetail userDetail) {
        if (hasUserId()) {
            userDetail.setUserId(getUserId());
        }
        int count = corgiUserTestService.countUserNickname(userDetail.getNickname());
        if (count > 0) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
        }
        userDetail.setCheckStatus(AliyunGreenService.PASS);
        userDetail = aliyunGreenService.checkDesc(userDetail);
        userDetail.setGroup(changeGroup(userDetail.getGroup()));
        userDetail.setPreferGroup(changeGroupList(userDetail.getPreferGroup()));
        String result = corgiUserTestService.addDetail(userDetail);
        return getJsonResult(result);
    }

    @PostMapping("/update_user")
    public JsonResult updateUser(@RequestBody UserDetail userDetail) {
        if (hasUserId()) {
            userDetail.setUserId(getUserId());
        }
        String key = "update_user-" + getUserId();
        if (!redisTemplate.opsForValue().setIfAbsent(key, System.currentTimeMillis() + "", 5l, TimeUnit.SECONDS)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "更新太频繁");
        }
        if (AliyunGreenService.TEXT_FORBIDDEN.equals(userDetail.getDesc())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "审核中，无法更新");
        }
        if (userDetail.getAvatar() != null && userDetail.getAvatar().contains(UserDetail.VERIFIED)) {
            userDetail.setAvatarCheckStatus(UserDetail.VERIFIED);
        }
        if (hasUserId()) {
            userDetail = aliyunGreenService.checkBackground(userDetail);
            userDetail = aliyunGreenService.checkAvatar(userDetail);
            userDetail = aliyunGreenService.checkDesc(userDetail);
            userDetail.setGroup(changeGroup(userDetail.getGroup()));
        }
        String result = corgiUserService.updateDetail(userDetail);
        return getJsonResult(result);
    }

    @GetMapping("/check_nickname")
    public JsonResult checkNickname(@RequestParam("nickname") String nickname) {
        if (StringUtils.isEmpty(nickname)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称为空");
        }
        System.out.println(aliyunGreenService.checkText(nickname));
//        int count = corgiUserService.countUserNickname(nickname);
//        if (count > 0) {
//            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
//        }
        return new JsonResult();
    }

    @GetMapping("/default_avatar")
    public JsonResult getDefaultAvatar() {
        Integer index = new Random().nextInt(AVATRS.size());
        return new JsonResult(AVATRS.get(index));
    }

    @GetMapping("/default_nickname")
    public JsonResult getDefaultNickname() {
        String nickname = getNickname();
        for (int i = 0; i < 5; i++) {
            if (corgiUserService.countUserNickname(nickname) == 0) {
                break;
            }
            nickname = getNickname();
        }
        return new JsonResult(nickname);
    }

    @PostMapping("/update_nickname")
    public JsonResult updateNickname(@RequestBody UserDetail userDetail) {
        if (hasUserId()) {
            userDetail.setUserId(getUserId());
        }
        if (StringUtils.isEmpty(userDetail.getUserId())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "userId为空");
        }
        if (StringUtils.isEmpty(userDetail.getNickname())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称为空");
        }
        if (AliyunGreenService.TEXT_FORBIDDEN.equals(userDetail.getNickname())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "审核中，无法更新");
        }

        String result;
        if (aliyunGreenService.checkText(userDetail.getNickname()).isPass()) {
            result = corgiUserService.updateUserNickname(userDetail.getUserId(), userDetail.getNickname(), "");
            userDetail.setCheckStatus(AliyunGreenService.PASS);
            corgiUserService.updateDetail(userDetail);
        } else {
            result = corgiUserService.updateUserNickname(userDetail.getUserId(), AliyunGreenService.TEXT_FORBIDDEN, userDetail.getNickname());
            userDetail.setCheckStatus(AliyunGreenService.CHECK);
            corgiUserService.updateDetail(userDetail);
            mailService.sendCheckMessage("用户：", userDetail.getUserId());
        }
        if (!CorgiConstants.SUCCESS.equals(result)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
        }
        return getJsonResult(result);
    }

    @PostMapping("/update_prefer_group")
    public JsonResult updatePreferGroup(@RequestBody UserDetail userDetail) {
        if (hasUserId()) {
            userDetail.setUserId(getUserId());
        }
        List<String> preferGroup = new ArrayList<>();
        if (!CollectionUtils.isEmpty(userDetail.getPreferGroup())) {
            for (String group : userDetail.getPreferGroup()) {
                preferGroup.add(changeGroup(group));
            }
        }
        String result = corgiUserService.updatePreferGroup(userDetail.getUserId(), preferGroup);
        return getJsonResult(result);
    }

    @GetMapping("/delete_user_background")
    public JsonResult deleteUserBackground() {
        UserDetail userDetail = new UserDetail();
        userDetail.setUserId(getUserId());
        userDetail.setBackground(CheckPic.getDefaultBackground());
        userDetail.setBgDataId("-");
        userDetail.setBgCheckStatus(AliyunGreenService.PASS);
        corgiUserService.updateDetail(userDetail);
        return new JsonResult();
    }

    @GetMapping("/delete_user_sound")
    public JsonResult deleteUserSound() {
        corgiSoundService.deleteCorgiSound(getUserId());
        return new JsonResult();
    }

    @GetMapping("/play_user_sound")
    public JsonResult deleteUserSound(@RequestParam("userId") String userId) {
        CorgiSound corgiSound = new CorgiSound();
        corgiSound.setUserId(userId);
        corgiSoundService.updateCorgiSound(corgiSound);
        return new JsonResult();
    }

    @GetMapping("/get_user_sound")
    public JsonResult getUserSound(@RequestParam("userId") String userId) {
        return new JsonResult(corgiSoundService.getCorgiSound(userId));

    }

    @GetMapping("/add_user_sound")
    public JsonResult addUserSound(@RequestParam("soundUrl") String url) {
        CorgiSound sound = aliyunGreenService.checkSound(url, getUserId());
        corgiSoundService.deleteCorgiSound(getUserId());
        if (CorgiSound.NORMAL.equals(sound.getStatus())) {
            corgiSoundService.addCorgiSound(sound);
        }
        return new JsonResult(sound);
    }

    @GetMapping("/delete_user_pic")
    public JsonResult deleteUserPic(@RequestParam("picId") String picId) {
        String result = corgiPicService.deleteUserPic(picId, getUserId());
        return getJsonResult(result);
    }

    @PostMapping("/update_user_pic")
    public JsonResult updateUserPic(@RequestBody UserPic userPic) {
        if (hasUserId()) {
            userPic.setUserId(getUserId());
        }
        if (StringUtils.isEmpty(userPic.getUserId())) {
            userPic.setUserId(getUserId());
        }
        List<UserPic> userPics = (List<UserPic>) aliyunGreenService.checkPic(Arrays.asList(userPic), userPic.getUserId(), CheckPic.USER);
        String result = corgiPicService.updateUserPic(userPics.get(0));
        return getJsonResult(result);
    }

    @PostMapping("/add_user_pic")
    public JsonResult addUserPic(@RequestBody UserPic userPic) {
        if (hasUserId()) {
            userPic.setUserId(getUserId());
        }
        List<UserPic> userPics = (List<UserPic>) aliyunGreenService.checkPic(Arrays.asList(userPic), userPic.getUserId(), CheckPic.USER);
        String result = corgiPicService.addUserPic(userPics.get(0));
        userPic.setPicId(result);
        return new JsonResult(userPic);
    }

    @GetMapping("/get_vip_info")
    public JsonResult getVipInfo(@RequestParam("userId") String userId) {
        String expireDate = corgiUserService.getUserVipExpire(userId);
        CorgiUserVipDetail detail = new CorgiUserVipDetail();
        if (StringUtils.isEmpty(expireDate) || "-".equals(expireDate)) {
            return new JsonResult(detail);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            Date date = sdf.parse(expireDate);
            detail.setRemainDate((date.getTime() - new Date().getTime()) / (1000 * 3600 * 24));
            detail.setExpireDate(expireDate);
        } catch (ParseException e) {
            return new JsonResult(detail);
        }
        return new JsonResult(detail);
    }

    @GetMapping("/get_user_detail")
    public JsonResult getUserDetail(@RequestParam("userId") String userId, @RequestParam(name = "loginUserId", required = false) String loginUserId) {
        try {
            if (hasUserId()) {
                loginUserId = getUserId();
            }
            UserDetail userDetail = corgiUserService.getUserDetail(userId, loginUserId);
            if (userDetail == null) {
                log.info(" user:{} detail code:{} ", userId, Constants.PARAMETER_ERROR_CODE);
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "用户不存在");
            }
            if (userDetail.getRole() == null) {
                userDetail.setRole("");
            }
            userDetail.setMatch(0.0);
            if (getUserId().equals(userId)) {
                if (CorgiPic.NEED_CHECK.equals(userDetail.getAvatarCheckStatus())) {
                    CheckPic pic = corgiPicService.getCheckPicByDataId(userDetail.getAvatarDataId());
                    if (pic != null) {
                        userDetail.setAvatar(pic.getPicUrl());
                    }
                }
                if (CorgiPic.NEED_CHECK.equals(userDetail.getBgCheckStatus())) {
                    CheckPic pic = corgiPicService.getCheckPicByDataId(userDetail.getBgDataId());
                    if (pic != null) {
                        userDetail.setBackground(pic.getPicUrl());
                    }
                }
            }
            return new JsonResult(userDetail);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            log.info("detail code:{} ", Constants.SERVER_ERROR_CODE);
            return new JsonResult(Constants.SERVER_ERROR_CODE, e.getMessage());
        }
    }

    @GetMapping("/get_user_data")
    public JsonResult getUserData(@RequestParam("userId") String userId) {
        Integer follow = corgiUserFollowService.countFollow(userId);
        Integer fans = corgiUserFollowService.countFollowed(userId);
        Integer getLike = corgiLikeService.countUserLikeByDate(userId, null);
        return new JsonResult(new UserData(userId, follow, fans, getLike));
    }

    @GetMapping("/visit")
    public JsonResult visit(@RequestParam("userId") String userId) {
        corgiVisitService.visit(getUserId(), userId);
        HashMap extra = new HashMap();
        extra.put("type", "203");
        mqService.sendSilentMessage(PushMessage.builder()
                .sourceUserId(getUserId())
                .targetUserId(userId)
                .message("有人访问你啦")
                .extra(extra)
                .build());
        return new JsonResult();
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
            token.setWorkflowId("aaf163046a83f3462053a51b8a8bf634");
            return new JsonResult(token);
        } catch (ClientException e) {
            log.error(e.getMessage(), e);
            return new JsonResult(Constants.SYS_ERROR_CODE, e.getErrMsg());
        }
    }

    @PostMapping("/update_user_position")
    public JsonResult updateUserPosition(@RequestBody UserPosition userPosition) throws PermissionException {
        if (userPosition.getLat() == null) {
            userPosition.setLat(1000.0);
        }
        if (userPosition.getLng() == null) {
            userPosition.setLng(1000.0);
        }
        HashMap result = new HashMap();
        result.put("freq", 1);
        try {
            String jwt = RequestUtil.getJwt();
            if (!StringUtils.isEmpty(jwt)) {
                DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
                String jwtUserId = decodedJWT.getClaim("userId").asString();
                log.info("updating user:{} ", jwtUserId);
                if (JWTUtils.ADMIN_ID.equals(jwtUserId)) {
                    result.put("jwt", JWTUtils.createJWT(userPosition.getUserId(), userPosition.getVersion()));
                } else if (!userPosition.getUserId().equals(jwtUserId)) {
                    log.error("非当前用户");
                    return new JsonResult(Constants.PERMISSION_ERROR_CODE, "非当前用户");
                } else {
                    UserLogin u = corgiUserService.getUserLogin(jwtUserId);
                    if (u == null || StringUtils.isEmpty(u.getUserId())) {
                        log.error("用户不存在:" + jwtUserId + " v:" + userPosition.getVersion());
                        return new JsonResult(Constants.PERMISSION_ERROR_CODE, "用户不存在");
                    }
                    Date expireDate = decodedJWT.getExpiresAt();
                    if (expireDate.getTime() - System.currentTimeMillis() < JWTUtils.expireTime) {
                        result.put("jwt", JWTUtils.createJWT(jwtUserId, userPosition.getVersion()));
                    }
                }
                if (userPosition.getLat() < 200 && userPosition.getLng() < 200) {
                    HashMap extra = new HashMap();
                    extra.put("lat", userPosition.getLat());
                    extra.put("lng", userPosition.getLng());
                    extra.put("type", PushMessage.MATCH_90_MESSAGE_TYPE);
                    extra.put("userId", userPosition.getUserId());
                    mqService.sendMessage(PushMessage.builder()
                            .type(PushMessage.MATCH)
                            .message(PushMessage.MATCH_90_MESSAGE)
                            .sourceUserId(userPosition.getUserId())
                            .extra(extra)
                            .build());
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new PermissionException(Constants.SERVER_ERROR_CODE, e.getMessage());
        }
        UserPosition oldPosition = corgiUserService.getUserPosition(userPosition.getUserId());
        userPosition.setChannel(RequestUtil.getChannel());
        if (oldPosition != null) {
            result.put("city", oldPosition.getCity());
            corgiUserService.updateUserPosition(userPosition);
        } else {
            corgiUserService.updateUserPosition(userPosition);
        }
        if (redisTemplate.opsForValue().setIfAbsent("update_user_" + userPosition.getUserId(), "1", 5L, TimeUnit.MINUTES)) {
            UserDetail userDetail = corgiUserService.getUserDetailBasic(userPosition.getUserId());
            if (userDetail != null) {
                userDetail.setTime(System.currentTimeMillis());
                if (!"influencer".equals(userDetail.getAvatarStatus())) {
                    userDetail.setAvatarStatus(corgiUserService.getUserVipExpire(userDetail.getUserId()));
                }
                corgiMatchService.updateUser(userDetail);
            }
        }
        String expireDate = corgiUserService.getUserVipExpire(userPosition.getUserId());
        CorgiUserVipDetail detail = new CorgiUserVipDetail();
        if (StringUtils.isNotEmpty(expireDate) && !"-".equals(expireDate)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            try {
                Date date = sdf.parse(expireDate);
                detail.setRemainDate(((date.getTime() - new Date().getTime()) / (1000 * 3600 * 24)));
                detail.setExpireDate(expireDate);
            } catch (Exception e) {

            }
        }
        result.put("remainDate", detail.getRemainDate());
        result.put("expireDate", detail.getExpireDate());
        return new JsonResult(result);
    }

    @GetMapping("/get_nearby_user")
    public JsonResult getNearbyUser(UserQuery userQuery) {
        userQuery.setGroup(changeGroupList(userQuery.getGroup()));
        List<UserProfile> userProfiles = corgiUserService.getNearByUser(userQuery);
        CorgiActivity corgiActivity = new CorgiActivity();
        corgiActivity.setStatus(CorgiActivity.CREATED);
        try {
            for (UserProfile userProfile : userProfiles) {
//                String key = "activity_count_" + userProfile.getUserId();
//                String count = redisTemplate.opsForValue().get(key);
//                if (StringUtils.isEmpty(count) || !StringUtils.isNumeric(count)) {
//                    corgiActivity.setUserId(userProfile.getUserId());
//                    long finalCount = corgiActivityService.countCorgiActivity(corgiActivity);
//                    userProfile.setActivityCount((int) finalCount);
//                    redisTemplate.opsForValue().set(key, finalCount + "", 1, TimeUnit.HOURS);
//                } else {
//                    userProfile.setActivityCount(Integer.parseInt(count));
//                }
                userProfile.setActivityCount(0);
//                userProfile.setSounds(corgiSoundService.getCorgiSound(userProfile.getUserId()));
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonResult(userProfiles);
    }

    @GetMapping("/get_all_nearby_user")
    public JsonResult getAllNearbyUser(UserQuery userQuery) {
        List<UserProfile> userProfiles = corgiUserService.getAllNearByUserProfile(userQuery);
        return new JsonResult(userProfiles);
    }

    @GetMapping("/send_code")
    public JsonResult sendToken(@RequestParam("telNo") String telNo) {
        log.info("sending code to: {}  ", telNo);
        List<String> blockTel = corgiBlacklistService.getBeBlacked("-1");
        if (blockTel.contains(telNo)) {
            return new JsonResult(Constants.API_ERROR_CODE, "该号码无法注册");
        }
        if (redisTemplate.hasKey("suspended_number_" + telNo)) {
            return new JsonResult(Constants.API_ERROR_CODE, "该号码暂时无法注册");
        }
        if (telNo.startsWith("170") || telNo.startsWith("171")) {
            return new JsonResult(Constants.API_ERROR_CODE, "为了保护平台用户权益，将不允许商业虚拟手机号注册，请更换号码后再注册。");
        }
        Random random = new Random();
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) ra;
        HttpServletRequest hrequest = sra.getRequest();
        String ip = IPUtil.getIpAddr(hrequest);
        String port = IPUtil.getPort(hrequest);
        String telKey = "tel_" + telNo;
        if (redisTemplate.hasKey(telKey)) {
            return new JsonResult(Constants.API_ERROR_CODE, "请求太频繁");
        }
        String ipKey = "ip_tel_" + ip;
        String tel = redisTemplate.opsForValue().get(ipKey);
        if (StringUtils.isNotEmpty(tel) && !tel.equals(telNo)) {
            log.info("duplicate ip...{}:{} tel:{}   ", ip, port, telNo);
            return new JsonResult(Constants.API_ERROR_CODE, "请求太频繁");
        }
        redisTemplate.opsForValue().set(ipKey, telNo, 5, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("tel_" + telNo, telNo, 50, TimeUnit.SECONDS);
        String code = "";
        for (int i = 0; i < 4; i++) {
            code += random.nextInt(10);
        }
        telNo = telNo.replaceAll("\\+", "");
        redisTemplate.opsForValue().set(CODE_PREFIX + telNo, code, 5, TimeUnit.MINUTES);
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);

        String sign = "SMS_180049529";
        String signName = "可基";
        //String signName = "Corgi";
        if (telNo.contains("-")) {
            sign = "SMS_188570616";
            signName = "Corgi";
        }
        log.info("to {} sending code:{}", telNo, code);
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");
        request.putQueryParameter("RegionId", "cn-hangzhou");
        request.putQueryParameter("PhoneNumbers", telNo.replaceAll("-", ""));
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", sign);
        request.putQueryParameter("TemplateParam", "{\"code\":\"" + code + "\"}");
        try {
            CommonResponse response = client.getCommonResponse(request);
            log.info(telNo + "-result:" + response.getData());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonResult();
    }

    @GetMapping("get_user_questions")
    public JsonResult getUserQuestions() {
        return new JsonResult(CharacterUtils.getUserQuestions());
    }

    @GetMapping("follow")
    public JsonResult follow(@RequestParam("userId") String userId, @RequestParam("targetUserId") String targetUserId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        if (userId.equals(targetUserId)) {
            return new JsonResult();
        }
        int follow = corgiUserFollowService.isFollowed(userId, targetUserId);
        if (follow != 1 && follow != 3) {
            corgiUserFollowService.follow(userId, targetUserId);
            HashMap extra = new HashMap();
            mqService.sendMessage(PushMessage.builder()
                    .type(PushMessage.FOLLOW)
                    .sourceUserId(userId)
                    .targetUserId(targetUserId)
                    .extra(extra)
                    .build());
        }

        return new JsonResult();
    }

    @GetMapping("unfollow")
    public JsonResult unfollow(@RequestParam("userId") String userId, @RequestParam("targetUserId") String targetUserId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiUserFollowService.unfollow(userId, targetUserId);
        return new JsonResult();
    }

    @GetMapping("is_followed")
    public JsonResult isFollowed(@RequestParam("userId") String userId, @RequestParam(name = "targetUserId", required = false) String targetUserId) {
        if (StringUtils.isEmpty(targetUserId)) {
            return new JsonResult(0);
        }
        int result = corgiUserFollowService.isFollowed(userId, targetUserId);
        return new JsonResult(result);
    }

    @GetMapping("get_follow_user")
    public JsonResult getFollowUser(@RequestParam("userId") String userId, @RequestParam("type") String type,
                                    @RequestParam(name = "lat", required = false) Double lat,
                                    @RequestParam(name = "lng", required = false) Double lng,
                                    @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getFollowUserByPage(userId, type, lat, lng, page, pageSize);
        return new JsonResult(userProfiles);
    }

    @GetMapping("get_share_user")
    public JsonResult getShareUser(@RequestParam("userId") String userId, @RequestParam(required = false, name = "name") String name,
                                   @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getShareUserByPage(userId, name, page, pageSize);
        if (StringUtils.isNotEmpty(name)) {
            return new JsonResult(groupByShare(userProfiles, userId));
        } else {
            return new JsonResult(userProfiles);
        }
    }

    @GetMapping("get_match_user")
    public JsonResult getMatchUser(@RequestParam("userId") String userId, @RequestParam("type") String type,
                                   @RequestParam(name = "lat", required = false) Double lat,
                                   @RequestParam(name = "lng", required = false) Double lng,
                                   @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getMatchUserByPage(userId, type, lat, lng, page, pageSize);
        return new JsonResult(userProfiles);
    }

    @GetMapping("get_history_followed_user")
    public JsonResult getHistoryFollowedUser(@RequestParam("userId") String userId,
                                             @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getFollowUserHistoryByPage(userId, page, pageSize);
        return new JsonResult(userProfiles);
    }

    @GetMapping("get_be_followed_user")
    public JsonResult getBeFollowedUser(@RequestParam("userId") String userId,
                                        @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getFollowedUserByPage(userId, 0L, page, pageSize);
        return new JsonResult(userProfiles);
    }

    @GetMapping("read_follow")
    public JsonResult readFollow(@RequestParam("userId") String userId, @RequestParam("followedUserId") String followedUserId) {
        corgiUserFollowService.readFollowUser(followedUserId, userId);
        return new JsonResult();
    }

    @GetMapping("count_follow_user")
    public JsonResult countFollowUser(@RequestParam("userId") String userId) {
        int count = corgiUserFollowService.countFollow(userId);
        return new JsonResult(count);
    }

    @GetMapping("count_match_user")
    public JsonResult countMatchUser(@RequestParam("userId") String userId) {
        int count = corgiUserFollowService.countMatch(userId);
        return new JsonResult(count);
    }

    @GetMapping("count_be_followed")
    public JsonResult countBeFollowed(@RequestParam("userId") String userId) {
        int count = corgiUserFollowService.countFollowed(userId);
        return new JsonResult(count);
    }

    @GetMapping("count_all_be_followed")
    public JsonResult countAllBeFollowed(@RequestParam("userId") String userId) {
        int count = corgiUserFollowService.countAllFollowed(userId);
        return new JsonResult(count);
    }


    @GetMapping("update_user_tag")
    public JsonResult updateUserTag(@RequestParam("userId") String userId, @RequestParam(required = false, name = "tags") List<String> tags) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiToolService.updateUserTag(userId, tags);
        return new JsonResult();
    }

    @GetMapping("update_user_interest")
    public JsonResult updateUserInterest(@RequestParam("userId") String userId, @RequestParam("category") String category, @RequestParam(required = false, name = "interests") List<String> interests) {
        if (hasUserId()) {
            userId = getUserId();
        }
        if (interests == null) {
            return new JsonResult();
        }
        corgiToolService.updateUserInterest(userId, category, interests);
        return new JsonResult();
    }

    @PostMapping("feedback")
    public JsonResult feedback(@RequestBody MailMessage mailMessage) {
        try {
            mailService.sendMail(mailMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonResult();
    }

    @GetMapping("block")
    public JsonResult block(@RequestParam("userId") String userId, @RequestParam("blockId") String blockId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        String result = corgiBlacklistService.addBlacklist(userId, blockId);
        if (CorgiConstants.SUCCESS.equals(result)) {
            String blackKey = "black_cache_" + getUserId();
            if (redisTemplate.hasKey(blackKey)) {
                redisTemplate.opsForList().leftPush(blackKey, blockId);
            }
            return new JsonResult("拉黑成功");
        } else {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "请不要重复拉黑");
        }
    }

    @GetMapping("unblock")
    public JsonResult unblock(@RequestParam("userId") String userId, @RequestParam("blockId") String blockId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        String result = corgiBlacklistService.deleteBlacklist(userId, blockId);
        if (CorgiConstants.SUCCESS.equals(result)) {
            return new JsonResult("取消拉黑成功");
        } else {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "对方不在黑名单中");
        }
    }

    @GetMapping("get_blacklist")
    public JsonResult getBlacklist(@RequestParam("userId") String userId) {
        return new JsonResult(corgiBlacklistService.getBlackUser(userId));
    }


    @GetMapping("test")
    public JsonResult test(@RequestParam("userId") String userId, @RequestParam("blockId") String blockId) {
        corgiBlacklistService.addBlacklist(userId, blockId);
        return new JsonResult();
    }

    @GetMapping("get_billboard")
    public JsonResult getBillboard() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new Date());
        List<UserProfile> userProfiles = corgiBillboardService.getBillboard(date);
        if (hasUserId()) {
            String userId = getUserId();
            for (UserProfile userProfile : userProfiles) {
                int count = corgiUserFollowService.isFollowed(userId, userProfile.getUserId());
                userProfile.setIsFollowed(count);
            }
        }
        return new JsonResult(userProfiles);
    }

    @GetMapping("get_influencer")
    public JsonResult getInfluencer(@RequestParam(name = "tel", required = false) String telNo,
                                    @RequestParam(name = "nickname", required = false) String nickname,
                                    @RequestParam(name = "character", required = false) String character,
                                    @RequestParam("page") Integer page,
                                    @RequestParam("pageSize") Integer pageSize) {
        UserDetail userDetail = new UserDetail();
        userDetail.setTelNo(telNo);
        userDetail.setNickname(nickname);
        userDetail.setCharacter(character);
        return new JsonResult(corgiUserService.searchInfluencer(userDetail, null, page, pageSize));
    }

    @GetMapping("set_influencer_character")
    public JsonResult setInfluencerCharacter(@RequestParam(name = "userId") String userId,
                                             @RequestParam(name = "character", required = false) String character) {
        UserDetail userDetail = new UserDetail();
        userDetail.setUserId(userId);
        userDetail.setCharacter(character);
        return new JsonResult(corgiUserService.updateDetail(userDetail));
    }

    @GetMapping("/call_user_city")
    public JsonResult callCity(@RequestParam("city") String city, @RequestParam(required = false, name = "userId") String userId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        String LockKey = "call_user_city_lock_" + userId;
        if (!corgiUtilService.tryLock(LockKey, "1", 20L, TimeUnit.SECONDS)) {
            return new JsonResult();
        }
        String key = getCallUserCityKey(userId);
        if (key == null) {
            return new JsonResult(Constants.API_ERROR_CODE, "这周新人新城次数已超过五次");
        }
        redisTemplate.opsForValue().set(key, System.currentTimeMillis() + "", 7, TimeUnit.DAYS);
        HashMap extra = new HashMap();
        UserDetail userDetail = corgiUserService.getUserDetail(getUserId(), null);
        extra.put("type", 904);
        extra.put("title", userDetail.getNickname().concat("到达了你的城市"));
        extra.put("picUrl", userDetail.getAvatar());
        extra.put("desc", "快去打个招呼吧！");
        extra.put("userId", userDetail.getUserId());
        extra.put("city", city);
        mqService.sendMessage(PushMessage.builder()
                .type(PushMessage.CITY + "_user")
                .message("一位小伙伴到达了你的城市")
                .sourceUserId(userId)
                .extra(extra)
                .build());
        return new JsonResult();
    }

    public String getCallUserCityKey(String userId) {
        for (int i = 1; i <= 5; i++) {
            String key = CALL_USER_CITY_PREFIX.concat(i + "_").concat(userId);
            if (!redisTemplate.hasKey(key)) {
                return key;
            }
        }
        return null;
    }

    @GetMapping("get_visitors")
    public JsonResult getVisitor(@RequestParam(required = false, name = "userId") String userId
            , @RequestParam(name = "page", required = false, defaultValue = "1") Integer page
            , @RequestParam("size") Integer size) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiVisitService.getVisitor(userId, page, size));
    }

    @GetMapping("get_visited")
    public JsonResult getVisited(@RequestParam(required = false, name = "userId") String userId
            , @RequestParam(name = "page", required = false, defaultValue = "1") Integer page
            , @RequestParam("size") Integer size) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiVisitService.getVisited(userId, page, size));
    }

    @GetMapping("get_visited_by_count")
    public JsonResult getVisitedByCount(@RequestParam(required = false, name = "userId") String userId
            , @RequestParam(name = "page", required = false, defaultValue = "1") Integer page
            , @RequestParam("size") Integer size) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiVisitService.getVisitedByCount(userId, page, size));
    }

    @GetMapping("get_visitor_by_count")
    public JsonResult getVisitorByCount(@RequestParam(required = false, name = "userId") String userId
            , @RequestParam(name = "page", required = false, defaultValue = "1") Integer page
            , @RequestParam("size") Integer size) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiVisitService.getVisitorByCount(userId, page, size));
    }

    @GetMapping("count_visit")
    public JsonResult countVisit(@RequestParam(required = false, name = "userId") String userId) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiVisitService.countVisit(userId));
    }

    @GetMapping("count_visit_unread")
    public JsonResult countVisitUnread(@RequestParam(required = false, name = "userId") String userId) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiVisitService.countVisitUnread(userId));
    }

    @GetMapping("get_city_new_user")
    public JsonResult getCityNewUser(@RequestParam(name = "city", required = false, defaultValue = "") String city) {
        return new JsonResult(corgiUserService.recommendUser(city, getUserId()));
    }

    @GetMapping("get_map_user")
    public JsonResult getMapUser(Map userQueryMap) {
        String userQueryJson = JSONObject.toJSONString(userQueryMap);
        log.info("map:{} ", userQueryJson);
        UserQuery userQuery = JSONObject.parseObject(userQueryJson, UserQuery.class);
        userQuery.setUserId(getUserId());
        MapUserProfile mapUserProfile = corgiUserService.getMapUser(userQuery);
        return new JsonResult(mapUserProfile);
    }

    @GetMapping("get_user_by_ids")
    public JsonResult getUserByIds(@RequestParam("userIds") String userIds) {
        String[] ids = userIds.split(",");
        List<UserDetail> profiles = new ArrayList<>();
        Long threshold = System.currentTimeMillis() - 2 * 60000;
        for (int i = 0; i < ids.length; i++) {
            UserDetail userDetail = corgiUserService.getUserDetailBasic(ids[i]);
            if (userDetail != null) {
                String expireDate = corgiUserService.getUserVipExpire(userDetail.getUserId());
                userDetail.setVip(!org.springframework.util.StringUtils.isEmpty(expireDate) && !"-".equals(expireDate));
                profiles.add(userDetail);
                UserPosition position = corgiUserService.getUserPosition(userDetail.getUserId());
                if (position != null && position.getUptime() != null && position.getUptime() > threshold) {
                    userDetail.setOnlineStatus(1);
                } else {
                    userDetail.setOnlineStatus(0);
                }
            }
        }
        return new JsonResult(profiles);
    }

    @GetMapping("update_date_status")
    public JsonResult updateDateStatus(@RequestParam("status") String status) {
        CorgiDate corgiDate = new CorgiDate();
        corgiDate.setUserId(getUserId());
        corgiDate.setStatus(status);
        corgiUserDateService.updateDate(corgiDate);
        return new JsonResult();
    }

    @PostMapping("update_date")
    public JsonResult updateDate(@RequestBody() CorgiDate date) {
        if (hasUserId()) {
            date.setUserId(getUserId());
        }
        corgiUserDateService.addDate(date);
        return new JsonResult();
    }

    @GetMapping("get_verify_token")
    public JsonResult getVerifyToken() throws PermissionException {
        String userId = getUserId();
        if (!hasUserId()) {
            userId = "1";
        }
        UserDetail detail = corgiUserService.getUserDetailBasic(userId);
        if (detail != null) {
            return new JsonResult(aliyunGreenService.getDescribeVerifyToken(detail));
        } else {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "找不到用户");
        }
    }

    @GetMapping("get_verify_result")
    public JsonResult getVerifyResult(@RequestParam("requestId") String requestId) throws PermissionException {
        DescribeVerifyResultResponse response = aliyunGreenService.getDescribeVerifyResult(requestId);
        String userId = getUserId();
        log.info("user:{} verity result: {} ", userId, response.getFaceComparisonScore());
        Float score = response.getFaceComparisonScore();
        if (score != null && score > 40) {
            UserDetail userDetail = new UserDetail();
            userDetail.setUserId(userId);
            userDetail.setAvatarCheckStatus(UserDetail.VERIFIED);
            corgiUserService.updateDetail(userDetail);
            return new JsonResult("verified");
        }
        return new JsonResult(UserDetail.NO_FACE);
    }

    @GetMapping("get_compare_result")
    public JsonResult getCompareResult(@RequestParam("avatar") String avatar) throws PermissionException {
        String userId = getUserId();
        if (!hasUserId()) {
            userId = "1";
        }
        UserDetail userDetail = corgiUserService.getUserDetailBasic(userId);
        if (userDetail != null && UserDetail.VERIFIED.equals(userDetail.getAvatarCheckStatus())) {
            CompareFacesResponse response = aliyunGreenService.compareAvatar(userDetail.getAvatar(), avatar);
            if (response.getData() != null) {
                Float score = response.getData().getSimilarityScore();
                if (score != null && score > 80) {
                    return new JsonResult("verified");
                }
            }
        }
        return new JsonResult(UserDetail.NO_FACE);
    }

    @GetMapping("has_remind_paying")
    public JsonResult hasRemindPaying(@RequestParam("userId") String userId) {
        if (redisTemplate.hasKey("remind_paying-" + userId + "-" + getUserId())) {
            return new JsonResult(true);
        }
        return new JsonResult(false);
    }

    @GetMapping("remind_paying")
    public JsonResult remindPaying(@RequestParam("userId") String userId) {
        mqService.sendMessage(buildRemindPaying(userId));
        redisTemplate.opsForValue().set("remind_paying-" + userId + "-" + getUserId(), System.currentTimeMillis() + "", 30L, TimeUnit.DAYS);
        return new JsonResult(false);
    }

    private PushMessage buildRemindPaying(String userId) {
        PushMessage pushMessage = new PushMessage();
        pushMessage.setSourceUserId("corgihelper");
        pushMessage.setTargetUserId(userId);
        pushMessage.setMessage("有人想看到你发布付费动态");
        HashMap<String, Object> extra = new HashMap<>();
        extra.put("type", "907");
        JSONArray content = new JSONArray();
        UserDetail detail = corgiUserService.getUserDetailBasic(getUserId());
        content.add(new JSONObject().fluentPut("text", detail.getNickname()).fluentPut("url", detail.getUserId())
                .fluentPut("urlType", "4").fluentPut("isBold", true));
        content.add(new JSONObject().fluentPut("text", " 想看到你发布付费动态"));
        extra.put("content", content);
        extra.put("bottomText", "去发布>");
        extra.put("bottomUrlType", "11");
        pushMessage.setExtra(extra);
        return pushMessage;
    }

    private UserShare groupByShare(List<UserProfile> userProfiles, String userId) {
        UserShare share = new UserShare();
        if (CollectionUtils.isEmpty(userProfiles)) {
            return share;
        }
        for (UserProfile userProfile : userProfiles) {
            int follow = corgiUserFollowService.isFollowed(userId, userProfile.getUserId());
            if (follow >= 3) {
                share.getMatchUsers().add(userProfile);
            } else {
                share.getFollowUsers().add(userProfile);
            }
        }
        return share;
    }


    public static String changeGroup(String group) {
        if ("猴子".equals(group)) {
            return "偏瘦";
        }
        if ("野狼".equals(group)) {
            return "精壮";
        }
        if ("奶狗".equals(group)) {
            return "匀称";
        }
        if ("狒狒".equals(group)) {
            return "肌肉";
        }
        if ("壮熊".equals(group)) {
            return "肉壮";
        }
        if ("胖熊".equals(group)) {
            return "偏胖";
        }
        return group;
    }

    private String getNickname() {
        StringBuffer sb = new StringBuffer("小可_");
        for (int i = 0; i < 5; i++) {
            sb.append(Math.round(Math.random() * 25 + 65));
        }
        return sb.toString();
    }

    public static List<String> changeGroupList(List<String> groups) {
        if (CollectionUtils.isEmpty(groups)) {
            return groups;
        }
        List<String> result = new ArrayList<>();
        for (String group : groups) {
            result.add(changeGroup(group));
        }
        return result;
    }
}

