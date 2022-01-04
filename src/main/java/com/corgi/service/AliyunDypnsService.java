package com.corgi.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.*;
import com.corgi.entity.SMSRequest;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Random;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class AliyunDypnsService {

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;
    private Client managementClient;

    @PostConstruct
    void init() throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        this.managementClient = new Client(config);
    }

    public String getMobile(String accessToken) {
        GetMobileRequest request = new GetMobileRequest();
        request.setAccessToken(accessToken);
        try {
            GetMobileResponse response = managementClient.getMobile(request);
            log.info(new Gson().toJson(response));
            return response.getBody().getGetMobileResultDTO().getMobile();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public boolean verifyMobile(String mobile, String accessToken) {
        VerifyMobileRequest request = new VerifyMobileRequest();
        request.setAccessCode(accessToken);
        request.setPhoneNumber(mobile);
        try {
            VerifyMobileResponse response = managementClient.verifyMobile(request);
            log.info(new Gson().toJson(response));
            return "PASS".equals(response.getBody().getGateVerifyResultDTO().getVerifyResult());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    public GetSmsAuthTokensResponseBody getSMSToken(SMSRequest request) throws Exception {
        GetSmsAuthTokensRequest getSmsAuthTokensRequest = new GetSmsAuthTokensRequest()
                .setBundleId(request.getBundleId())
                .setOsType(request.getOsType())
                .setPackageName(request.getPackageName())
                .setSceneCode(request.getSceneCode())
                .setExpire(900L)
                .setSmsCodeExpire(300);
        String telNo = request.getTelNo();
        telNo = telNo.replaceAll("\\+", "");
        String sign = "SMS_180049529";
        if (telNo.contains("-")) {
            sign = "SMS_188570616";
        }
        getSmsAuthTokensRequest.setSmsTemplateCode(sign);
        getSmsAuthTokensRequest.setSignName(request.getSignName());
        // 复制代码运行请自行打印 API 的返回值
        GetSmsAuthTokensResponse response = managementClient.getSmsAuthTokens(getSmsAuthTokensRequest);
        return response.getBody();
    }

    public boolean verifySmsToken(String code, String token, String telNo) {
        VerifySmsCodeRequest request = new VerifySmsCodeRequest()
                .setSmsCode(code).setPhoneNumber(telNo)
                .setSmsToken(token);
        try {
            VerifySmsCodeResponse response = managementClient.verifySmsCode(request);
            return response.getBody().data;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}
