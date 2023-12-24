package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.com.viapi.FileUtils;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.cloudauth.model.v20190307.*;

import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.facebody.model.v20191230.RecognizeFaceRequest;
import com.aliyuncs.facebody.model.v20191230.RecognizeFaceResponse;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.green.model.v20180509.TextScanRequest;
import com.aliyuncs.green.model.v20180509.VoiceSyncScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.saf.model.v20180919.ExecuteRequestRequest;
import com.aliyuncs.saf.model.v20180919.ExecuteRequestResponse;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.CorgiHttpUtil;
import com.corgi.common.util.IPUtil;
import com.corgi.entity.*;
import com.corgi.exception.PermissionException;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiSoundService;
import com.corgi.user.entity.CorgiSound;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPic;
import com.aliyuncs.facebody.model.v20191230.DetectFaceRequest;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class AliyunGreenService {
    public static final String TEXT_FORBIDDEN = "(内容审核中)";


    public static String CHECK = "check";
    public static String PASS = "pass";
    public static String FAIL = "fail";
    public static String NOT_GOOD = "not_good";
    public static List<String> CHECK_LIST = Arrays.asList("check", "not_good", "fail");
    public static List<String> BLOCK_WORD_LIST = Arrays.asList("飞机杯", "打飞机", "初一", "初二", "初三", "男高", "粗口"
            "高一", "高二", "高三", "初中", "高中", "小学生", "sao0", "sao的", "骚的", "奴", "贱", "革命", "起义", "嫖", "娼",
            "无毛", "按摩", "有偿", "技师", "捆绑", "调教", "学生党", "求C", "约p", "约P", "可飞", "乳头",
            "精液", "色情", "原味", "无套");

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;

    String REGION_ID = "cn-shanghai";

    private IAcsClient managementClient;

    private static final String IMAGE_INFO = "?x-oss-process=image/info";
    private Random random = new Random(System.currentTimeMillis());

    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiSoundService corgiSoundService;

    @Autowired
    private MailService mailService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;


    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);
    }

    public DescribeVerifyTokenResponse getDescribeVerifyToken(UserDetail userDetail) throws PermissionException {
        String requestId = UUID.randomUUID().toString().replaceAll("-", "");
        DescribeVerifyTokenRequest request = new DescribeVerifyTokenRequest();
        request.setSysProtocol(ProtocolType.HTTPS);
        request.setBizId(requestId);
        request.setBizType("corgi-avatar");
        request.setFaceRetainedImageUrl(userDetail.getAvatar());
        try {
            DescribeVerifyTokenResponse response = managementClient.getAcsResponse(request);
            response.setRequestId(requestId);
            return response;
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
            throw new PermissionException(Constants.PARAMETER_ERROR_CODE, "获取token失败");
        }
    }

    public DescribeVerifyResultResponse getDescribeVerifyResult(String requestId) throws PermissionException {
        DescribeVerifyResultRequest verifyResultRequest = new DescribeVerifyResultRequest();
        verifyResultRequest.setSysProtocol(ProtocolType.HTTPS);
        verifyResultRequest.setBizId(requestId);
        verifyResultRequest.setBizType("corgi-avatar");
        try {
            return managementClient.getAcsResponse(verifyResultRequest);
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
            throw new PermissionException(Constants.PARAMETER_ERROR_CODE, "获取验证结果失败");
        }
    }

    public CompareFacesResponse compareAvatar(String oldAvatar, String newAvatar) throws PermissionException {
        CompareFacesRequest request = new CompareFacesRequest();
        request.setSysMethod(MethodType.POST);
        request.setSourceImageType("FacePic");
        request.setSourceImageValue(newAvatar);
        request.setTargetImageType("FacePic");
        request.setTargetImageValue(oldAvatar);
        try {
            return managementClient.getAcsResponse(request);
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
            return new CompareFacesResponse();
        }
    }

    public UserDetail checkBackground(UserDetail userDetail) {
        if (StringUtils.isEmpty(userDetail.getBackground())) {
            return userDetail;
        }
        UserPic corgiPic = new UserPic();
        corgiPic.setStatus(CorgiPic.NORMAL);
        corgiPic.setPicUrl(userDetail.getBackground());
        corgiPic = (UserPic) checkPic(Arrays.asList(corgiPic), userDetail.getUserId(), CheckPic.BACKGROUND).get(0);
        if (CheckPic.NEED_CHECK.equals(corgiPic.getStatus())) {
            mailService.sendCheckMessage("用户背景：", userDetail.getUserId());
            userDetail.setBgCheckStatus(corgiPic.getStatus());
            userDetail.setBgDataId(corgiPic.getDataId());
            userDetail.setBackground(null);
            return userDetail;
        }
        userDetail.setBgCheckStatus(corgiPic.getStatus());
        userDetail.setBgDataId(corgiPic.getDataId());
        return userDetail;
    }

    public UserDetail checkAvatar(UserDetail userDetail) {
        if (StringUtils.isEmpty(userDetail.getAvatar())) {
            return userDetail;
        }
        UserPic corgiPic = new UserPic();
        corgiPic.setStatus(CorgiPic.NORMAL);
        corgiPic.setPicUrl(userDetail.getAvatar());
        corgiPic = (UserPic) checkPic(Arrays.asList(corgiPic), userDetail.getUserId(), CheckPic.AVATAR).get(0);
        if (CheckPic.NEED_CHECK.equals(corgiPic.getStatus())) {
            mailService.sendCheckMessage("用户头像：", userDetail.getUserId());
            userDetail.setAvatarCheckStatus(corgiPic.getStatus());
            userDetail.setAvatarDataId(corgiPic.getDataId());
            userDetail.setAvatar(null);
            return userDetail;
        }
        if (UserDetail.VERIFIED.equals(userDetail.getAvatarCheckStatus())) {
            return userDetail;
        }
        corgiPic = (UserPic) checkFace(corgiPic, userDetail.getUserId());
        if (StringUtils.isEmpty(userDetail.getAvatarCheckStatus())) {
            userDetail.setAvatarCheckStatus(corgiPic.getStatus());
        }
        userDetail.setAvatarDataId(corgiPic.getDataId());
        return userDetail;
    }

    public UserDetail checkDesc(UserDetail userDetail) {
        String desc = userDetail.getDesc();
        CheckTextResult textResult = this.checkText(desc);
        if (!textResult.isPass()) {
            userDetail.setDesc(textResult.getContent());
            mqService.sendAdminMessage(userDetail.getUserId(),"经系统检测发现您的个人介绍【"+textResult.getOriginContent()+"】涉嫌违规，已被系统自动屏蔽，请自觉维护社群健康发展。");
        }
        return userDetail;
    }

    public CorgiPic checkFace(CorgiPic pic, String sourceId) {
        log.info("pics = " + pic.getPicUrl());

        RecognizeFaceRequest request = new RecognizeFaceRequest();
        request.setImageURL(this.getUrl(pic.getPicUrl()));
        try {
            RecognizeFaceResponse response = managementClient.getAcsResponse(request);
            RecognizeFaceResponse.Data data = response.getData();
            log.info(JSONObject.toJSONString(data));
            if (data.getFaceCount() > 0) {
                return pic;
            }
            pic.setStatus(UserDetail.NO_FACE);
            addCheckPic(pic, sourceId, CheckPic.AVATAR);
        } catch (ServerException e) {
            log.error(e.getMessage(), e);
            pic.setStatus(UserDetail.NO_FACE);
            addCheckPic(pic, sourceId, CheckPic.AVATAR);
        } catch (ClientException e) {
            if ("InvalidImage.NotFoundFace".equals(e.getErrCode())) {
                log.info("ErrCode:" + e.getErrCode());
                log.info("ErrMsg:" + e.getErrMsg());
                log.info("RequestId:" + e.getRequestId());
            } else {
                log.error("ErrCode:" + e.getErrCode());
                log.error("ErrMsg:" + e.getErrMsg());
                log.error("RequestId:" + e.getRequestId());
            }
            pic.setStatus(UserDetail.NO_FACE);
            addCheckPic(pic, sourceId, CheckPic.AVATAR);
        }
        return pic;
    }

    public String getUrl(String url) {
        try {
            FileUtils fileUtils = FileUtils.getInstance(accessKeyId, accessKeySecret);
            return fileUtils.upload(url);
        } catch (ClientException | IOException e) {
            e.printStackTrace();
        }
        return url;
    }

    public CorgiSound checkSound(String url, String sourceId) {
        if (StringUtils.isEmpty(url)) {
            return null;
        }
        log.info("sound = " + url);

        CorgiSound sound = new CorgiSound();
        sound.setSoundUrl(url);
        sound.setDataId(getDataId());
        sound.setStatus(CorgiPic.NORMAL);
        sound.setUserId(sourceId);

        VoiceSyncScanRequest asyncScanRequest = new VoiceSyncScanRequest();
        // 指定API返回格式。
        asyncScanRequest.setAcceptFormat(FormatType.JSON);
        // 指定请求方法。
        asyncScanRequest.setMethod(com.aliyuncs.http.MethodType.POST);
        asyncScanRequest.setRegionId("cn-shanghai");
        asyncScanRequest.setConnectTimeout(3000);
        // 由于同步语音检测比较耗时，因此建议将超时时间设置在15秒以上。
        asyncScanRequest.setReadTimeout(15000);

        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task1 = new LinkedHashMap<>();
        // 请将下面的地址修改为要检测的语音文件的地址。
        task1.put("url", url);
        tasks.add(task1);
        JSONObject data = new JSONObject();

        data.put("scenes", Arrays.asList("antispam"));
        data.put("tasks", tasks);

        System.out.println(JSON.toJSONString(data, true));
        try {
            asyncScanRequest.setHttpContent(data.toJSONString().getBytes("UTF-8"), "UTF-8", FormatType.JSON);
            HttpResponse httpResponse = managementClient.doAction(asyncScanRequest);

            if (httpResponse.isSuccess()) {
                JSONObject scrResponse = JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
                if (200 == scrResponse.getInteger("code")) {
                    JSONArray taskResults = scrResponse.getJSONArray("data");
                    for (Object taskResult : taskResults) {
                        Integer code = ((JSONObject) taskResult).getInteger("code");
                        JSONArray sceneResults = ((JSONObject) taskResult).getJSONArray("results");
                        if (200 == code) {
                            for (Object sceneResult : sceneResults) {
                                sound.setResult(getText((JSONObject) sceneResult));
                                String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                                if (!suggestion.equals("pass")) {
                                    sound.setStatus(CorgiPic.NEED_CHECK);
                                    break;
                                }
                            }
                        } else {
                            sound.setResult("task process fail: " + JSON.toJSONString(taskResult));
                        }
                    }
                } else {
                    sound.setResult("detect not success. code: " + scrResponse.getInteger("code"));
                }
            } else {
                sound.setResult("response fail:" + new String(httpResponse.getHttpContent(), "UTF-8"));
            }
        } catch (Exception e) {
            sound.setResult(e.getMessage());
            log.error(e.getMessage(), e);
        }

        return sound;
    }

    public List<? extends CorgiPic> checkPic(List<? extends CorgiPic> urls, String sourceId, String type) {
        return checkPic(urls, sourceId, type, "sexy_pic");
    }

    public List<? extends CorgiPic> checkPic(List<? extends CorgiPic> urls, String sourceId, String type, String bizType) {
        if (CollectionUtils.isEmpty(urls)) {
            return null;
        }
        log.info("pics = " + urls);

        ImageSyncScanRequest imageSyncScanRequest = new ImageSyncScanRequest();
        // 指定api返回格式
        imageSyncScanRequest.setAcceptFormat(FormatType.JSON);
        // 指定请求方法
        imageSyncScanRequest.setMethod(MethodType.POST);
        imageSyncScanRequest.setEncoding("utf-8");
        //支持http和https
        imageSyncScanRequest.setProtocol(ProtocolType.HTTP);


        JSONObject httpBody = new JSONObject();
        /**
         * 设置要检测的场景, 计费是按照该处传递的场景进行
         * 一次请求中可以同时检测多张图片，每张图片可以同时检测多个风险场景，计费按照场景计算
         * 例如：检测2张图片，场景传递porn、terrorism，计费会按照2张图片鉴黄，2张图片暴恐检测计算
         * porn: porn表示色情场景检测
         */
        httpBody.put("scenes", Arrays.asList("porn", "ad", "terrorism"));

        /**
         * 设置待检测图片， 一张图片一个task
         * 多张图片同时检测时，处理的时间由最后一个处理完的图片决定
         * 通常情况下批量检测的平均rt比单张检测的要长, 一次批量提交的图片数越多，rt被拉长的概率越高
         * 这里以单张图片检测作为示例, 如果是批量图片检测，请自行构建多个task
         */
        List<JSONObject> tasks = new ArrayList<>();
        Date now = new Date();
        Map<String, CorgiPic> picMap = new HashMap<>();
        for (CorgiPic pic : urls) {
            JSONObject task = new JSONObject();
            String id = getDataId();
            task.put("dataId", id);
            pic.setDataId(id);
            picMap.put(id, pic);
            //设置图片链接
            task.put("url", pic.getPicUrl());
            task.put("time", now);
            tasks.add(task);
        }
        httpBody.put("tasks", tasks);
        httpBody.put("bizType", bizType);

        imageSyncScanRequest.setHttpContent(org.apache.commons.codec.binary.StringUtils.getBytesUtf8(httpBody.toJSONString()),
                "UTF-8", FormatType.JSON);
        /**
         * 请设置超时时间, 服务端全链路处理超时时间为10秒，请做相应设置
         * 如果您设置的ReadTimeout小于服务端处理的时间，程序中会获得一个read timeout异常
         */
        imageSyncScanRequest.setConnectTimeout(3000);
        imageSyncScanRequest.setReadTimeout(10000);
        HttpResponse httpResponse = null;
        try {
            httpResponse = managementClient.doAction(imageSyncScanRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //服务端接收到请求，并完成处理返回的结果
        if (httpResponse != null && httpResponse.isSuccess()) {
            JSONObject scrResponse = JSON.parseObject(org.apache.commons.codec.binary.StringUtils.newStringUtf8(httpResponse.getHttpContent()));
            int requestCode = scrResponse.getIntValue("code");
            //每一张图片的检测结果
            JSONArray taskResults = scrResponse.getJSONArray("data");
            if (200 == requestCode) {
                for (Object taskResult : taskResults) {
                    log.info(((JSONObject) taskResult).toJSONString());
                    String dataId = ((JSONObject) taskResult).getString("dataId");
                    CorgiPic pic = picMap.get(dataId);
                    //单张图片的处理结果
                    int taskCode = ((JSONObject) taskResult).getIntValue("code");
                    //图片要检测的场景的处理结果, 如果是多个场景，则会有每个场景的结果
                    JSONArray sceneResults = ((JSONObject) taskResult).getJSONArray("results");
                    if (200 == taskCode) {
                        boolean needCheck = false;
                        for (Object sceneResult : sceneResults) {
                            String scene = ((JSONObject) sceneResult).getString("scene");
                            String label = ((JSONObject) sceneResult).getString("label");
                            Double rate = ((JSONObject) sceneResult).getDouble("rate");
                            String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                            if (!suggestion.equals("pass")) {
                                pic.setStatus(CorgiPic.NEED_CHECK);
                                pic.setResult(suggestion + "-" + scene + "-" + label + "-" + rate);
                                needCheck = true;
                                break;
                            }
                        }
                        if (!needCheck) {
                            pic.setStatus(CorgiPic.NORMAL);
                            pic.setResult("pass");
                        }
                        addCheckPic(pic, sourceId, type);
                    } else {
//                        if (retry > 0) {
//                            try {
//                                Thread.sleep(1000L);
//                            } catch (InterruptedException e) {
//                                log.error(e.getMessage(), e);
//                            }
//                            return checkPic(urls, sourceId, type, retry--);
//                        }
                        String result = "task process fail. task response:" + JSON.toJSONString(taskResult);
                        pic.setStatus(CorgiPic.NEED_CHECK);
                        pic.setResult(result);
                        addCheckPic(pic, sourceId, type);
                        //单张图片处理失败, 原因视具体的情况详细分析
                        log.info(result);
                    }
                }
            } else {
                /**
                 * 表明请求整体处理失败，原因视具体的情况详细分析
                 */
                for (CorgiPic pic : urls) {
                    pic.setStatus(CorgiPic.NEED_CHECK);
                    String result = JSON.toJSONString("the whole image scan request failed. response:" + JSON.toJSONString(scrResponse));
                    pic.setResult(result);
                    addCheckPic(pic, sourceId, type);
                    log.info(result);
                }
            }
        }
        return urls;
    }

    public PicInfo getAliyunPicInfo(String url) {
        String[] urlArr = url.split("\\?x-oss-process");
        url = urlArr[0];
        PicInfo picInfo = new PicInfo();
        String result;
        try {
            result = redisTemplate.opsForValue().get(url);
            if (!StringUtils.isEmpty(result) && result.split("_").length == 2) {
                String[] hw = result.split("_");
                picInfo.setHeight(Long.valueOf(hw[0]));
                picInfo.setWidth(Long.valueOf(hw[1]));
                return picInfo;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        result = CorgiHttpUtil.doGet(url + IMAGE_INFO, null, null);
        try {
            JSONObject image = JSONObject.parseObject(result);
            if (image != null && image.getJSONObject("ImageHeight") != null && image.getJSONObject("ImageWidth") != null) {
                boolean revert = false;
                JSONObject orientObject = image.getJSONObject("Orientation");
                if (orientObject != null) {
                    Integer orientation = orientObject.getInteger("value");
                    revert = orientation != null && orientation > 4;
                }
                Long height = image.getJSONObject("ImageHeight").getLong("value");
                Long weight = image.getJSONObject("ImageWidth").getLong("value");
                if (revert) {
                    Long tmp = height;
                    height = weight;
                    weight = tmp;
                }
                picInfo.setHeight(height);
                picInfo.setWidth(weight);
                redisTemplate.opsForValue().set(url, height + "_" + weight, 3, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("get pic info:" + url + " failed", e);
        }
        return picInfo;
    }

    private void addCheckPic(CorgiPic corgiPic, String sourceId, String type) {
        CheckPic checkPic = new CheckPic();
        BeanUtils.copyProperties(corgiPic, checkPic);
        checkPic.setUserId(sourceId);
        checkPic.setType(type);
        corgiPicService.addCheckPic(checkPic);
    }

    public CheckTextResult checkText(String text) {
        return checkText(text, "sexy_pic");
    }

    public CheckTextResult checkText(String text, String bussType) {
        CheckTextResult textResult = new CheckTextResult();
        if (StringUtils.isEmpty(text)) {
            return textResult;
        }
        textResult.setOriginContent(text);
        for (String word : BLOCK_WORD_LIST) {
            if (text.contains(word)) {
                textResult.setPass(false);
                text = text.replaceAll(word, "**");
                textResult.setContent(text);
            }
        }
        TextScanRequest textScanRequest = new TextScanRequest();
        textScanRequest.setAcceptFormat(FormatType.JSON);
        textScanRequest.setHttpContentType(FormatType.JSON);
        textScanRequest.setMethod(com.aliyuncs.http.MethodType.POST);
        textScanRequest.setEncoding("UTF-8");
        textScanRequest.setRegionId("cn-shanghai");
        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task1 = new LinkedHashMap<>();
        task1.put("dataId", getDataId());
        /**
         * 待检测的文本，长度不超过10000个字符
         */
        task1.put("content", text);
        tasks.add(task1);
        JSONObject data = new JSONObject();

        /**
         * 检测场景，文本垃圾检测传递：antispam
         **/
        data.put("scenes", Arrays.asList("antispam"));
        data.put("tasks", tasks);
        data.put("bizType", bussType);
        System.out.println(JSON.toJSONString(data, true));
        // 请务必设置超时时间
        textScanRequest.setConnectTimeout(3000);
        textScanRequest.setReadTimeout(6000);
        try {
            textScanRequest.setHttpContent(data.toJSONString().getBytes("UTF-8"), "UTF-8", FormatType.JSON);
            HttpResponse httpResponse = managementClient.doAction(textScanRequest);
            if (httpResponse.isSuccess()) {
                JSONObject scrResponse = JSON.parseObject(new String(httpResponse.getHttpContent(), "UTF-8"));
                log.info("check text:{}", JSON.toJSONString(scrResponse, true));
                if (200 == scrResponse.getInteger("code")) {
                    JSONArray taskResults = scrResponse.getJSONArray("data");
                    for (Object taskResult : taskResults) {
                        if (200 == ((JSONObject) taskResult).getInteger("code")) {
                            String filteredContent = ((JSONObject) taskResult).getString("filteredContent");
                            if (!StringUtils.isEmpty(filteredContent)) {
                                textResult.setContent(filteredContent);
                            }
                            JSONArray sceneResults = ((JSONObject) taskResult).getJSONArray("results");
                            for (Object sceneResult : sceneResults) {
                                String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                                if (!"pass".equals(suggestion)) {
                                    textResult.setPass(false);
                                    return textResult;
                                }
                            }

                        } else {
                            log.error("task process fail:" + ((JSONObject) taskResult).getInteger("code"));
                        }
                    }
                } else {
                    log.error("detect not success. code:" + scrResponse.getInteger("code"));
                }
            } else {
                log.error("response not success. status:" + httpResponse.getStatus());
            }
        } catch (ServerException e) {
            log.error(e.getMessage(), e);
        } catch (ClientException e) {
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return textResult;
    }

    public CorgiActivity checkImageActivity(CorgiActivity activity) {
        String title = activity.getTitle();
        String content = activity.getContent();
        CheckTextResult titleResult = checkText(title);
        String forbiddenText = "";
        if (!StringUtils.isEmpty(title) && !titleResult.isPass()) {
            activity.setTitle(titleResult.getContent());
            activity.setCheckTitle(title);
            forbiddenText += titleResult.getOriginContent();
        }
        CheckTextResult contentResult = checkText(content);
        if (!StringUtils.isEmpty(content) && !contentResult.isPass()) {
            activity.setContent(contentResult.getContent());
            activity.setCheckContent(content);
            forbiddenText += contentResult.getOriginContent();
        }
        if (!StringUtils.isEmpty(forbiddenText)) {
            mqService.sendAdminMessage(activity.getUserId(), "经系统检测发现您的动态文案【" + forbiddenText + "】涉嫌违规，已被系统自动屏蔽，请自觉维护社群健康发展。");
        }
        return activity;
    }

    public CorgiActivity checkActivity(CorgiActivity activity) {
        String title = activity.getTitle();
        String content = activity.getContent();
        //boolean sendMail = false;
        CheckTextResult titleResult = checkText(title);
        if (!StringUtils.isEmpty(title) && !titleResult.isPass()) {
            activity.setTitle(titleResult.getContent());
            activity.setCheckTitle(title);
            //activity.setCheckStatus(CHECK);
            //sendMail = true;
        }
        CheckTextResult contentResult = checkText(content);
        if (!StringUtils.isEmpty(content) && !contentResult.isPass()) {
            activity.setContent(contentResult.getContent());
            activity.setCheckContent(content);
            //activity.setCheckStatus(CHECK);
            //sendMail = true;
        }
//        if (sendMail) {
//            mailService.sendCheckMessage("活动：", activity.getId());
//        }
        return activity;
    }

    public Double checkAccount(String mobile) {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) ra;
        HttpServletRequest hrequest = sra.getRequest();
        String ip = IPUtil.getIpAddr(hrequest);
        IClientProfile profile = DefaultProfile.getProfile("cn-shanghai", accessKeyId, accessKeySecret);
        DefaultProfile.addEndpoint("cn-shanghai", "saf", "saf.cn-shanghai.aliyuncs.com");
        IAcsClient client = new DefaultAcsClient(profile);

        ExecuteRequestRequest executeRequestRequest = new ExecuteRequestRequest();
        executeRequestRequest.setMethod(com.aliyuncs.http.MethodType.POST);
        executeRequestRequest.setService("account_abuse");
        // 业务详细参数，具体见文档里的业务参数部分,不需要的参数就不需要设置
        Map<String, Object> serviceParams = new HashMap<String, Object>();

        // 调用参数
        serviceParams.put("mobile", mobile);
        serviceParams.put("ip", ip);

        executeRequestRequest.setServiceParameters(JSONObject.toJSONString(serviceParams));
        /**
         * 请务必设置超时时间
         */
        executeRequestRequest.setReadTimeout(3000);
        try {
            executeRequestRequest.setHttpContent(JSONObject.toJSONString(serviceParams).getBytes("UTF-8"), "UTF-8", FormatType.JSON);
            ExecuteRequestResponse httpResponse = client.getAcsResponse(executeRequestRequest);
            log.info("no:{} ip:{} result:{}", mobile, ip, JSONObject.toJSONString(httpResponse));
            return Double.valueOf(httpResponse.getData().getScore());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0.0;
    }


    private String getDataId() {
        return UUID.randomUUID().toString() + random.nextInt(100);
    }

    private String getText(JSONObject sceneResult) {
        JSONArray details = sceneResult.getJSONArray("details");
        StringBuilder sb = new StringBuilder();
        for (Object detail : details) {
            sb.append(((JSONObject) detail).getString("text"));
        }
        return sb.toString();
    }

}
