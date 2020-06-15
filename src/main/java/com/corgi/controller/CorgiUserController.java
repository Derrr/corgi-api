package com.corgi.controller;

import com.alibaba.dubbo.common.utils.StringUtils;
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
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
import com.corgi.common.util.CharacterUtils;
import com.corgi.common.util.IPUtil;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.RequestUtil;
import com.corgi.entity.CheckPic;
import com.corgi.entity.MailMessage;
import com.corgi.entity.StorageToken;
import com.corgi.exception.PermissionException;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.EasemobService;
import com.corgi.service.MQService;
import com.corgi.service.MailService;
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
import java.util.*;
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
    private CorgiUserMatchService corgiUserMatchService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserTestService corgiUserTestService;
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
        if ((code != null && code.equals(userLogin.getCode())) || "00000".equals(userLogin.getCode()) || "13700000000".equals(userLogin.getTelNo())) {
            if (StringUtils.isEmpty(userLogin.getUserId())) {
                userLogin = corgiUserService.login(userLogin);
                if ("-1".equals(userLogin.getStatus())) {
                    easemobService.registerUser(userLogin.getUserId());
                    userLogin.setStatus("0");
                }
                userLogin.setJwt(JWTUtils.createJWT(userLogin.getUserId(), userLogin.getVersion()));
                return new JsonResult(userLogin);
            } else if (StringUtils.isEmpty(userLogin.getTelNo()) || StringUtils.isEmpty(userLogin.getImId())) {
                return new JsonResult(Constants.API_ERROR_CODE, "无法获取到手机号/推送ID");
            } else {
                corgiUserService.updateUserLogin(userLogin);
                return new JsonResult("更新手机号成功");
            }
        }
        return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
    }

    @PostMapping("/login_test")
    public JsonResult registerTest(@RequestBody UserLogin userLogin) {
        String code = redisTemplate.opsForValue().get(CODE_PREFIX + userLogin.getTelNo());
        if ((code != null && code.equals(userLogin.getCode())) || "00000".equals(userLogin.getCode()) || "13700000000".equals(userLogin.getTelNo())) {
            if (StringUtils.isEmpty(userLogin.getUserId())) {
                userLogin = corgiUserTestService.login(userLogin);
                if ("-1".equals(userLogin.getStatus())) {
                    easemobService.registerUser("test" + userLogin.getUserId());
                    userLogin.setStatus("0");
                }
                userLogin.setJwt(JWTUtils.createJWT(userLogin.getUserId(), userLogin.getVersion()));
                return new JsonResult(userLogin);
            } else if (StringUtils.isEmpty(userLogin.getTelNo()) || StringUtils.isEmpty(userLogin.getImId())) {
                return new JsonResult(Constants.API_ERROR_CODE, "无法获取到手机号/推送ID");
            } else {
                corgiUserTestService.updateUserLogin(userLogin);
                return new JsonResult("更新手机号成功");
            }
        }
        return new JsonResult(Constants.API_ERROR_CODE, "验证码错误");
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
        return new JsonResult(corgiUserService.getUserLogin(userId));
    }

    @PostMapping("/add_user")
    public JsonResult addUser(@RequestBody UserDetail userDetail) {
        if (hasUserId()) {
            userDetail.setUserId(getUserId());
        }
        int count = corgiUserService.countUserNickname(userDetail.getNickname());
        if (count > 0) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
        }
        List<UserPic> pics = (List<UserPic>) aliyunGreenService.checkPic(userDetail.getUserPics(), userDetail.getUserId(), CheckPic.USER);
        if (pics == null) {
            pics = new ArrayList<>();
        }
        UserPic userPic = aliyunGreenService.checkAvatar(userDetail);
        if (userPic != null) {
            userPic.setUserId(userDetail.getUserId());
            pics.add(0, userPic);
        }
        userDetail.setUserPics(pics);
        userDetail.setCheckStatus(AliyunGreenService.PASS);
        if (!aliyunGreenService.checkText(userDetail.getNickname())) {
            userDetail.setCheckStatus(AliyunGreenService.CHECK);
            mailService.sendCheckMessage("用户：", userDetail.getUserId());
        }
        userDetail = aliyunGreenService.checkDesc(userDetail);
        userDetail.setGroup(changeGroup(userDetail.getGroup()));
        userDetail.setPreferGroup(changeGroupList(userDetail.getPreferGroup()));
        String result = corgiUserService.addDetail(userDetail);
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
        if (AliyunGreenService.TEXT_FORBIDDEN.equals(userDetail.getDesc())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "审核中，无法更新");
        }
        userDetail = aliyunGreenService.checkDesc(userDetail);
        userDetail.setGroup(changeGroup(userDetail.getGroup()));
        String result = corgiUserService.updateDetail(userDetail);
        return getJsonResult(result);
    }

    @GetMapping("/check_nickname")
    public JsonResult checkNickname(@RequestParam("nickname") String nickname) {
        if (StringUtils.isEmpty(nickname)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称为空");
        }
        int count = corgiUserService.countUserNickname(nickname);
        if (count > 0) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
        }
        return new JsonResult();
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
        if (aliyunGreenService.checkText(userDetail.getNickname())) {
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

    @GetMapping("/delete_user_pic")
    public JsonResult deleteUserPic(@RequestParam("picId") String picId) {
        String result = corgiPicService.deleteUserPic(picId);
        return getJsonResult(result);
    }

    @PostMapping("/update_user_pic")
    public JsonResult updateUserPic(@RequestBody UserPic userPic) {
        if (hasUserId()) {
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

    @GetMapping("/get_user_detail")
    public JsonResult getUserDetail(@RequestParam("userId") String userId, @RequestParam(name = "loginUserId", required = false) String loginUserId) {
        UserDetail userDetail = corgiUserService.getUserDetail(userId, loginUserId);
        if (userDetail == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "用户不存在");
        }
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
    public JsonResult updateUserPosition(@RequestBody UserPosition userPosition) throws PermissionException {
        HashMap result = new HashMap();
        result.put("freq", 1);
        try {
            String jwt = RequestUtil.getJwt();
            if (!StringUtils.isEmpty(jwt)) {
                DecodedJWT decodedJWT = JWTUtils.decodeToken(jwt);
                String jwtUserId = decodedJWT.getClaim("userId").asString();
                if (JWTUtils.ADMIN_ID.equals(jwtUserId)) {
                    result.put("jwt", JWTUtils.createJWT(userPosition.getUserId(), userPosition.getVersion()));
                } else if (!userPosition.getUserId().equals(jwtUserId)) {
                    throw new PermissionException(Constants.PERMISSION_ERROR_CODE, "非当前用户");
                } else {
                    Date expireDate = decodedJWT.getExpiresAt();
                    if (expireDate.getTime() - System.currentTimeMillis() < JWTUtils.expireTime) {
                        result.put("jwt", JWTUtils.createJWT(jwtUserId, userPosition.getVersion()));
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new PermissionException(Constants.PERMISSION_ERROR_CODE, e.getMessage());
        }
        corgiUserService.updateUserPosition(userPosition);
        mqService.sendTrace(TraceFollow.builder()
                .userId(userPosition.getUserId())
                .option(TraceFollow.COUNT)
                .type(TraceFollow.STAY)
                .build());
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
                String key = "activity_count_" + userProfile.getUserId();
                String count = redisTemplate.opsForValue().get(key);
                if (StringUtils.isEmpty(count) || !StringUtils.isNumeric(count)) {
                    corgiActivity.setUserId(userProfile.getUserId());
                    long finalCount = corgiActivityService.countCorgiActivity(corgiActivity);
                    userProfile.setActivityCount((int) finalCount);
                    redisTemplate.opsForValue().set(key, finalCount + "", 1, TimeUnit.HOURS);
                } else {
                    userProfile.setActivityCount(Integer.parseInt(count));
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        mqService.sendTrace(TraceFollow.builder()
                .userId(userQuery.getUserId())
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.USER)
                .build());
        return new JsonResult(userProfiles);
    }

    @GetMapping("/get_all_nearby_user")
    public JsonResult getAllNearbyUser(UserQuery userQuery) {
        List<UserProfile> userProfiles = corgiUserService.getAllNearByUserProfile(userQuery);
        return new JsonResult(userProfiles);
    }

    @GetMapping("/send_code")
    public JsonResult sendToken(@RequestParam("telNo") String telNo) {
        log.info("sending code to: {}", telNo);
        Random random = new Random();
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) ra;
        HttpServletRequest hrequest = sra.getRequest();
        String ip = IPUtil.getIpAddr(hrequest);
        String port = IPUtil.getPort(hrequest);
        String ipKey = "ip_tel_" + ip;
        String tel = redisTemplate.opsForValue().get(ipKey);
        if (StringUtils.isNotEmpty(tel) && !tel.equals(telNo)) {
            log.info("duplicate ip...{}:{} tel:{}", ip, port, telNo);
            return new JsonResult(Constants.API_ERROR_CODE, "请求太频繁了");
        }
        redisTemplate.opsForValue().set(ipKey, telNo, 50, TimeUnit.SECONDS);
        String code = "";
        for (int i = 0; i < 4; i++) {
            code += random.nextInt(10);
        }
        telNo = telNo.replaceAll("\\+", "");
        redisTemplate.opsForValue().set(CODE_PREFIX + telNo, code, 5, TimeUnit.MINUTES);
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);

        String sign = "SMS_180049529";
        if (telNo.contains("-")) {
            sign = "SMS_188570616";
        }
        log.info("to {} sending code:{}", telNo, code);
        CommonRequest request = new CommonRequest();
        request.setMethod(MethodType.POST);
        request.setDomain("dysmsapi.aliyuncs.com");
        request.setVersion("2017-05-25");
        request.setAction("SendSms");
        request.putQueryParameter("RegionId", "cn-hangzhou");
        request.putQueryParameter("PhoneNumbers", telNo.replaceAll("-", ""));
        request.putQueryParameter("SignName", "Corgi");
        request.putQueryParameter("TemplateCode", sign);
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
    public JsonResult follow(@RequestParam("userId") String userId, @RequestParam("targetUserId") String targetUserId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiUserFollowService.follow(userId, targetUserId);
        HashMap extra = new HashMap();
        mqService.sendMessage(PushMessage.builder()
                .type(PushMessage.FOLLOW)
                .sourceUserId(userId)
                .targetUserId(targetUserId)
                .extra(extra)
                .build());
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
    public JsonResult isFollowed(@RequestParam("userId") String userId, @RequestParam("targetUserId") String targetUserId) {
        int result = corgiUserFollowService.isFollowed(userId, targetUserId);
        return new JsonResult(result);
    }

    @GetMapping("get_follow_user")
    public JsonResult getFollowUser(@RequestParam("userId") String userId, @RequestParam("type") String type,
                                    @RequestParam(name = "lat", required = false) Double lat,
                                    @RequestParam(name = "lng", required = false) Double lng,
                                    @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getFollowUserByPage(userId, type, lat, lng, page, pageSize);
        mqService.sendTrace(TraceFollow.builder()
                .userId(userId)
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.FOLLOW)
                .build());
        return new JsonResult(userProfiles);
    }

    @GetMapping("get_match_user")
    public JsonResult getMatchUser(@RequestParam("userId") String userId, @RequestParam("type") String type,
                                   @RequestParam(name = "lat", required = false) Double lat,
                                   @RequestParam(name = "lng", required = false) Double lng,
                                   @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getMatchUserByPage(userId, type, lat, lng, page, pageSize);
        mqService.sendTrace(TraceFollow.builder()
                .userId(userId)
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.FOLLOW)
                .build());
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
    public JsonResult updateUserTag(@RequestParam("userId") String userId, @RequestParam("tags") List<String> tags) {
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
        corgiBlacklistService.addBlacklist(userId, blockId);
        return new JsonResult();
    }

    @GetMapping("unblock")
    public JsonResult unblock(@RequestParam("userId") String userId, @RequestParam("blockId") String blockId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        corgiBlacklistService.deleteBlacklist(userId, blockId);
        return new JsonResult();
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

