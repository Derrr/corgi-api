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
            return getPosScore(pos) * pos + 75.0 * neu + getNegScore(neg) * neg;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private Double getPosScore(Double pos) {
        if (pos > 0.999) {
            return 100.0;
        }
        if (pos < 0.8) {
            return 90.0;
        }
        return 85.0 - 5 * Math.log10(1.0 - pos);
    }

    private Double getNegScore(Double neg) {
        if (neg > 0.999) {
            return 40.0;
        }
        if (neg < 0.8) {
            return 60.0;
        }
        return 70.0 + 10 * Math.log10(1.0 - neg);
    }

}
