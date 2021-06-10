package com.corgi.service;

import com.alibaba.fastjson.JSONObject;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.cloudauth.model.v20190307.DescribeVerifyTokenResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.alinlp.model.v20200629.*;
import com.corgi.common.constant.Constants;
import com.corgi.exception.PermissionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class AliyunNLPService {

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;
    private IAcsClient managementClient;
    String REGION_ID = "cn-hangzhou";

    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);
    }

    public Double getSaChe(String text) {
        GetSaChGeneralRequest request = new GetSaChGeneralRequest();
        request.setText(text);
        try {
            GetSaChGeneralResponse response = managementClient.getAcsResponse(request);
            JSONObject data = JSONObject.parseObject(response.getData());
            JSONObject result = data.getJSONObject("result");
            Double pos = result.getDouble("positive_prob");
            Double neg = result.getDouble("negative_prob");
            Double neu = result.getDouble("neutral_prob");
            log.info("tag:{} pos:{}, neu:{}, neu:{} ", text, pos, neg, neu);
            return (pos - neg + 1) * 50;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

}
