package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.entity.PicInfo;
import com.corgi.user.api.CorgiBarService;
import com.corgi.user.api.CorgiCommentService;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.entity.ActivityComment;
import com.corgi.user.entity.ActivityLike;
import com.corgi.user.entity.BarProfile;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Service
@Slf4j
public class CorgiUtilService {
    private final static PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager();

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Reference
    private CorgiBarService corgiBarService;

    private ThreadLocal<String> value = new ThreadLocal<>();

    @PostConstruct
    public void init() {
        poolConnManager.setMaxTotal(2000);
        poolConnManager.setDefaultMaxPerRoute(1000);
    }

    private CloseableHttpClient getCloseableHttpClient() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(poolConnManager)
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .build();

        return httpClient;
    }

    public String postJson(String url, HashMap message) {

        String result = null;
        CloseableHttpClient httpClient = getCloseableHttpClient();
        HttpPost httpPost = new HttpPost(url);
        CloseableHttpResponse response = null;
        try {

            httpPost.setHeader("Accept", "application/json;charset=UTF-8");
            httpPost.setHeader("Content-Type", "application/json");

            StringEntity stringEntity = new StringEntity(JSONObject.toJSONString(message));
            stringEntity.setContentType("application/json;charset=UTF-8");

            httpPost.setEntity(stringEntity);
            response = httpClient.execute(httpPost);
            if (response != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                result = EntityUtils.toString(response.getEntity(), Charset.defaultCharset());
                response.getEntity().getContent().close();
            } else if (response != null) {
                log.error("请求" + url + "获取失败, 状态异常：" + response.getStatusLine().getStatusCode());
                result = EntityUtils.toString(response.getEntity(), Charset.defaultCharset());
            }

        } catch (IOException e) {
            log.error("请求地址出错," + url + "错误信息:", e);
            httpPost.abort();
        } catch (IllegalArgumentException e) {
            log.error("返回参数错误", e);
            httpPost.abort();
        } finally {
            if (response != null) {
                try {
                    EntityUtils.consume(response.getEntity());
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    public boolean lock(String key) {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        value.set(id);
        while (!tryLock(key, id)) {
            try {
                if (System.currentTimeMillis() - now > 3000) {
                    return false;
                }
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public boolean tryLock(String key, String value) {
        return tryLock(key, value, 30L);
    }

    public boolean tryLock(String key, String value, Long time) {
        ValueOperations operations = redisTemplate.opsForValue();
        if (operations.setIfAbsent(key, value, time, TimeUnit.SECONDS)) {
            return true;
        }
        return false;
    }

    public void unlock(String key) {
        String id = redisTemplate.opsForValue().get(key);
        if (id != null && value.get().equals(id) && !"user".equals(key)) {
            redisTemplate.delete(key);
        }
    }

    public List<CorgiActivityDetail> convertUserActivityDetail(List<CorgiActivity> activityList, String userId, BarProfile barProfile) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(activityList)) {
            for (CorgiActivity activity : activityList) {
                Integer height = 0;
                Integer width = 0;
                if (!CollectionUtils.isEmpty(activity.getPics()) && activity.getPics().size() == 1) {
                    String picUrl = activity.getPics().get(0).getPicUrl();
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    height = picInfo.getHeight();
                    width = picInfo.getWidth();
                }
                Long likeCount = corgiLikeService.countActivityLike(activity.getId());
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), userId);
                List<ActivityLike> activityLikes = corgiLikeService.getFollowUser(userId, activity.getId());
                Long commentCount = corgiCommentService.countActivityComment(activity.getId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initSize(height, width);
                detail.setHasLike(hasLike);
                detail.setLikeCount(likeCount);
                detail.setLikeUsers(activityLikes);
                detail.setCommentCount(commentCount);
                detail.setBarDetail(barProfile);
                detailList.add(detail);
            }
        }
        return detailList;
    }
}
