package com.corgi.service;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.aliyuncs.vod.model.v20170321.*;
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

    public String getCoverUrl(String videoId) {
        ListSnapshotsRequest request = new ListSnapshotsRequest();
        request.setVideoId(videoId);
        try {
            ListSnapshotsResponse response = this.managementClient.getAcsResponse(request);
            return response.getMediaSnapshot().getSnapshots().get(0).getUrl();
        } catch (ClientException e) {
            log.error("ErrCode:" + e.getErrCode());
            log.error("ErrMsg:" + e.getErrMsg());
            log.error("RequestId:" + e.getRequestId());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return "";
    }

    public GetVideoInfoResponse getVideoUrl(String videoId) {
        GetVideoInfoRequest request = new GetVideoInfoRequest();
        request.setVideoId(videoId);
        try {
            log.info("preparing request info...");
            GetVideoInfoResponse response = this.managementClient.getAcsResponse(request);
            log.info("response... {} ", response);
            return response;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public GetMezzanineInfoResponse getVideoInfo(String videoId) {
        GetMezzanineInfoRequest request = new GetMezzanineInfoRequest();
        request.setVideoId(videoId);
        try {
            log.info("preparing request info...");
            GetMezzanineInfoResponse response = this.managementClient.getAcsResponse(request);
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

    public GetVideoListResponse getVideoList(Integer page, Integer pageSize, Long cateId) {
        GetVideoListRequest request = new GetVideoListRequest();
        request.setCateId(cateId);
        request.setPageNo(page);
        request.setPageSize(pageSize);
        try {
            return this.managementClient.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return null;
    }

    public CreateUploadVideoResponse getUploadToken(String title, String fileName) {
        CreateUploadVideoRequest request = new CreateUploadVideoRequest();
        request.setFileName(fileName);
        request.setTitle(title);
        request.setActionName("CreateUploadVideo");
        request.setWorkflowId("aaf163046a83f3462053a51b8a8bf634");
        try {
            return this.managementClient.getAcsResponse(request);
        } catch (ClientException e) {
            e.printStackTrace();
        }
        return null;
    }

}
