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
import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
import com.corgi.common.util.CharacterUtils;
import com.corgi.entity.CheckPic;
import com.corgi.entity.MailMessage;
import com.corgi.entity.StorageToken;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
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
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
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
        if ((code != null && code.equals(userLogin.getCode())) || "00000".equals(userLogin.getCode())) {
            if (StringUtils.isEmpty(userLogin.getUserId())) {
                userLogin = corgiUserService.login(userLogin);
                if ("-1".equals(userLogin.getStatus())) {
                    easemobService.registerUser(userLogin.getUserId());
                    userLogin.setStatus("0");
                }
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

    @PostMapping("/add_user")
    public JsonResult addUser(@RequestBody UserDetail userDetail) {
        List<UserPic> pics = (List<UserPic>) aliyunGreenService.checkPic(userDetail.getUserPics(), CheckPic.USER);
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
        }
        userDetail = aliyunGreenService.checkDesc(userDetail);
        String result = corgiUserService.addDetail(userDetail);
        return getJsonResult(result);
    }

    @PostMapping("/update_user")
    public JsonResult updateUser(@RequestBody UserDetail userDetail) {
        if (AliyunGreenService.TEXT_FORBIDDEN.equals(userDetail.getDesc())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "审核中，无法更新");
        }
        userDetail = aliyunGreenService.checkDesc(userDetail);
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
        } else {
            result = corgiUserService.updateUserNickname(userDetail.getUserId(), AliyunGreenService.TEXT_FORBIDDEN, userDetail.getNickname());
            userDetail.setCheckStatus(AliyunGreenService.CHECK);
            corgiUserService.updateDetail(userDetail);
        }
        if (!CorgiConstants.SUCCESS.equals(result)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称被抢啦！换一个试试？");
        }
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
        String key = "sentMatch_" + userPosition.getUserId();
        String matchTime = redisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(matchTime)) {
            String nowTime = System.currentTimeMillis() + "";
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
            redisTemplate.opsForValue().set(key, nowTime, 60L, TimeUnit.MINUTES);
        }
        mqService.sendTrace(TraceFollow.builder()
                .userId(userPosition.getUserId())
                .option(TraceFollow.COUNT)
                .type(TraceFollow.STAY)
                .build());
        return new JsonResult();
    }

    @GetMapping("/get_nearby_user")
    public JsonResult getNearbyUser(UserQuery userQuery) {
//        UserPosition userPosition = new UserPosition();
//        userPosition.setUserId(userQuery.getUserId());
//        userPosition.setLat(userQuery.getLat());
//        userPosition.setLng(userQuery.getLng());

        //corgiUserService.updateUserPosition(userPosition);
        List<UserProfile> userProfiles = corgiUserService.getNearByUser(userQuery);
        mqService.sendTrace(TraceFollow.builder()
                .userId(userQuery.getUserId())
                .option(TraceFollow.CHANGE)
                .type(TraceFollow.USER)
                .build());
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
    public JsonResult follow(@RequestParam("userId") String userId, @RequestParam("targetUserId") String targetUserId) {
        corgiUserFollowService.follow(userId, targetUserId);
        HashMap extra = new HashMap();
        extra.put("userId", userId);
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

    @GetMapping("get_history_followed_user")
    public JsonResult getHistoryFollowedUser(@RequestParam("userId") String userId,
                                             @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> userProfiles = corgiUserFollowService.getFollowUserHistoryByPage(userId, page, pageSize);
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

    @GetMapping("count_be_followed")
    public JsonResult countBeFollowed(@RequestParam("userId") String userId) {
        int count = corgiUserFollowService.countFollowed(userId);
        return new JsonResult(count);
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

    @PostMapping("feedback")
    public JsonResult feedback(@RequestBody MailMessage mailMessage) {
        try {
            mailService.sendMail(mailMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonResult();
    }

    @GetMapping("test")
    public JsonResult test() {
        UserDetail userDetail = new UserDetail();
        userDetail.setUserId("-1");
        userDetail.setAvatar("http://corgi-pic.oss-cn-beijing.aliyuncs.com/avatar/55/1581399343285");
        this.addUser(userDetail);
        return new JsonResult();
    }

}
