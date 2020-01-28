package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.green.model.v20180509.ImageSyncScanRequest;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.HttpResponse;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.corgi.common.util.CorgiHttpUtil;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiPic;
import com.corgi.entity.PicInfo;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.entity.UserDetail;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class AliyunGreenService {
    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;

    String REGION_ID = "cn-shanghai";

    private IAcsClient managementClient;

    private static final String IMAGE_INFO = "?x-oss-process=image/info";

    @Reference
    private CorgiPicService corgiPicService;

    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);
    }

    public UserDetail checkAvatar(UserDetail userDetail) {
        if (StringUtils.isEmpty(userDetail.getAvatar())) {
            return userDetail;
        }
        CorgiPic corgiPic = new CorgiPic();
        corgiPic.setPicUrl(userDetail.getAvatar());
        corgiPic = checkPic(Arrays.asList(corgiPic), CheckPic.AVATAR).get(0);
        userDetail.setAvatarStatus(corgiPic.getStatus());
        userDetail.setAvatarDataId(corgiPic.getDataId());
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
                        for (Object sceneResult : sceneResults) {
                            String scene = ((JSONObject) sceneResult).getString("scene");
                            String label = ((JSONObject) sceneResult).getString("label");
                            Double rate = ((JSONObject) sceneResult).getDouble("rate");
                            String suggestion = ((JSONObject) sceneResult).getString("suggestion");
                            if (!suggestion.equals("pass")) {
                                pic.setStatus(CorgiPic.NEED_CHECK);
                                pic.setResult(suggestion + "-" + scene + "-" + label + "-" + rate);
                                addCheckPic(pic, type);
                            } else {
                                pic.setStatus(CorgiPic.NORMAL);
                                pic.setResult(suggestion);
                            }
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
        String result = CorgiHttpUtil.doGet(url + IMAGE_INFO, null, null);
        try {
            JSONObject image = JSONObject.parseObject(result);
            picInfo.setHeight(image.getJSONObject("ImageHeight").getInteger("value"));
            picInfo.setWidth(image.getJSONObject("ImageWidth").getInteger("value"));
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
}
