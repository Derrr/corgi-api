package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.green.model.v20180509.TextScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.util.CorgiHttpUtil;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiPic;
import com.corgi.entity.MailMessage;
import com.corgi.entity.PicInfo;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPic;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
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

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;

    String REGION_ID = "cn-shanghai";

    private IAcsClient managementClient;

    private static final String IMAGE_INFO = "?x-oss-process=image/info";

    @Reference
    private CorgiPicService corgiPicService;

    @Autowired
    private MailService mailService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);

    }

    public UserPic checkAvatar(UserDetail userDetail) {
        if (StringUtils.isEmpty(userDetail.getAvatar())) {
            return null;
        }
        UserPic corgiPic = new UserPic();
        corgiPic.setPicUrl(userDetail.getAvatar());
        corgiPic = (UserPic) checkPic(Arrays.asList(corgiPic), CheckPic.USER).get(0);
        if (CheckPic.NEED_CHECK.equals(corgiPic.getStatus())) {
            mailService.sendCheckMessage("用户图片：", userDetail.getUserId());
        }
        return corgiPic;
    }

    public UserDetail checkDesc(UserDetail userDetail) {
        String desc = userDetail.getDesc();
        if (!this.checkText(desc)) {
            userDetail.setCheckDesc(desc);
            userDetail.setDesc(AliyunGreenService.TEXT_FORBIDDEN);
            userDetail.setCheckStatus(CHECK);
            mailService.sendCheckMessage("用户：", userDetail.getUserId());
        }
        return userDetail;
    }

    public List<? extends CorgiPic> checkPic(List<? extends CorgiPic> urls, String type) {
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
            String id = UUID.randomUUID().toString();
            task.put("dataId", id);
            pic.setDataId(id);
            picMap.put(id, pic);
            //设置图片链接
            task.put("url", pic.getPicUrl());
            task.put("time", now);
            tasks.add(task);
        }
        httpBody.put("tasks", tasks);
        httpBody.put("bizType", "sexy_pic");

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
            System.out.println(JSON.toJSONString(scrResponse, true));
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
                                addCheckPic(pic, type);
                                needCheck = true;
                                break;
                            }
                        }
                        if (!needCheck) {
                            pic.setStatus(CorgiPic.NORMAL);
                            pic.setResult("pass");
                        }
                    } else {
                        String result = "task process fail. task response:" + JSON.toJSONString(taskResult);
                        pic.setStatus(CorgiPic.NEED_CHECK);
                        pic.setResult(result);
                        addCheckPic(pic, type);
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
                    addCheckPic(pic, type);
                    log.info(result);
                }
            }
        }
        return urls;
    }

    public PicInfo getAliyunPicInfo(String url) {
        PicInfo picInfo = new PicInfo();
        String result = redisTemplate.opsForValue().get(url);
        try {
            if (!StringUtils.isEmpty(result) && result.split("_").length == 2) {
                String[] hw = result.split("_");
                picInfo.setHeight(Integer.valueOf(hw[0]));
                picInfo.setWidth(Integer.valueOf(hw[1]));
                return picInfo;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        result = CorgiHttpUtil.doGet(url + IMAGE_INFO, null, null);
        try {
            JSONObject image = JSONObject.parseObject(result);
            if (image != null && image.getJSONObject("ImageHeight") != null && image.getJSONObject("ImageWidth") != null) {
                Integer height = image.getJSONObject("ImageHeight").getInteger("value");
                Integer weight = image.getJSONObject("ImageWidth").getInteger("value");
                picInfo.setHeight(height);
                picInfo.setWidth(weight);
                redisTemplate.opsForValue().set(url, height + "_" + weight, 100, TimeUnit.DAYS);
            }
        } catch (Exception e) {
            log.error("get pic info:" + url + " failed", e);
        }
        return picInfo;
    }


    private void addCheckPic(CorgiPic corgiPic, String type) {
        CheckPic checkPic = new CheckPic();
        BeanUtils.copyProperties(corgiPic, checkPic);
        checkPic.setType(type);
        corgiPicService.addCheckPic(checkPic);
    }

    public boolean checkText(String text) {
        if (StringUtils.isEmpty(text)) {
            return true;
        }
        TextScanRequest textScanRequest = new TextScanRequest();
        textScanRequest.setAcceptFormat(FormatType.JSON);
        textScanRequest.setHttpContentType(FormatType.JSON);
        textScanRequest.setMethod(com.aliyuncs.http.MethodType.POST);
        textScanRequest.setEncoding("UTF-8");
        textScanRequest.setRegionId("cn-shanghai");
        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task1 = new LinkedHashMap<>();
        task1.put("dataId", UUID.randomUUID().toString());
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
        data.put("bizType", "sexy_pic");
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
                            JSONArray sceneResults = ((JSONObject) taskResult).getJSONArray("results");
                            for (Object sceneResult : sceneResults) {
                                String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                                if (!"pass".equals(suggestion)) {
                                    return false;
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
        return true;
    }

    public CorgiActivity checkActivity(CorgiActivity activity) {
        String title = activity.getTitle();
        String content = activity.getContent();
        String type = activity.getActivityType();
        boolean sendMail = false;
        if (!StringUtils.isEmpty(type) && !checkText(type)) {
            activity.setActivityType("待审核");
            activity.setCheckActivityType(type);
            activity.setCheckStatus(CHECK);
            sendMail = true;
        }
        if (!StringUtils.isEmpty(title) && !checkText(title)) {
            activity.setTitle(TEXT_FORBIDDEN);
            activity.setCheckTitle(title);
            activity.setCheckStatus(CHECK);
            sendMail = true;
        }
        if (!StringUtils.isEmpty(content) && !checkText(content)) {
            activity.setContent(TEXT_FORBIDDEN);
            activity.setCheckContent(content);
            activity.setCheckStatus(CHECK);
            sendMail = true;
        }
        if (sendMail) {
            mailService.sendCheckMessage("活动：", activity.getId());
        }
        return activity;
    }
}
