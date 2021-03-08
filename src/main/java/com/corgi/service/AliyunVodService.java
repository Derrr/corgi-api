package com.corgi.service;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.vod.model.v20170321.GetCategoriesRequest;
import com.aliyuncs.vod.model.v20170321.GetCategoriesResponse;
import com.aliyuncs.vod.model.v20170321.GetPlayInfoRequest;
import com.aliyuncs.vod.model.v20170321.GetPlayInfoResponse;
import com.corgi.common.JsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.annotation.PostConstruct;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class AliyunVodService {

    @Value("${aliyun.accessKeyId}")
    private String accessKeyId;

    @Value("${aliyun.AccessKeySecret}")
    private String accessKeySecret;
    private IAcsClient managementClient;
    String REGION_ID = "cn-shanghai";

    @PostConstruct
    void init() {
        IClientProfile profile = DefaultProfile.getProfile(REGION_ID, accessKeyId, accessKeySecret);
        this.managementClient = new DefaultAcsClient(profile);
    }

    public GetPlayInfoResponse getVideoUrl(String videoId) {
        GetPlayInfoRequest request = new GetPlayInfoRequest();
        request.setVideoId(videoId);
        try {
            log.info("preparing request info...");
            GetPlayInfoResponse response = this.managementClient.getAcsResponse(request);
            log.info("response... {} ", response);
            return response;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public GetCategoriesResponse getVideoCategory(Long categoryId) {
        GetCategoriesRequest request = new GetCategoriesRequest();
        request.setCateId(categoryId);
        request.setPageNo(1L);
        request.setPageSize(100L);
        try {
            return this.managementClient.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return null;
    }
}
