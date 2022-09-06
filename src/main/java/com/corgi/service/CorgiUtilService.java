package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.util.RequestUtil;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.entity.CorgiPic;
import com.corgi.entity.PicInfo;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
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
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Reference
    private CorgiBarService corgiBarService;
    @Reference
    private CorgiPicService corgiPicService;
    public static final List<String> CHANNELS = Arrays.asList("xiaomi","oppo", "vivo");

    private ThreadLocal<String> value = new ThreadLocal<>();

    @PostConstruct
    public void init() {
        poolConnManager.setMaxTotal(2000);
        poolConnManager.setDefaultMaxPerRoute(1000);
    }

    public boolean checkComment(List<ActivityComment> comments, String userId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        if (CollectionUtils.isEmpty(comments)) {
            return true;
        }
        Long nowTime = System.currentTimeMillis();
        Integer count = 0;
        Long commentTime = 0l;
        for (ActivityComment comment : comments) {
            if (comment.getCommentUserId().equals(userId)) {
                count++;
                try {
                    commentTime = sdf.parse(comment.getCtime()).getTime();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }
        if (count > 2 && nowTime - commentTime < 60000) {
            return false;
        }
        return true;
    }

    private CloseableHttpClient getCloseableHttpClient() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(poolConnManager)
                .setRetryHandler(new DefaultHttpRequestRetryHandler())
                .build();

        return httpClient;
    }

    public boolean isNewUser(String userId) {
        String channel = RequestUtil.getChannel();
        if (CHANNELS.contains(channel)) {
            UserLogin userLogin = corgiUserService.getUserLogin(userId);
            if ("17000000000".equals(userLogin.getTelNo())) {
                return true;
            }
            if (userLogin != null && userLogin.getCtime() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.HOUR, -24);
                return userLogin.getCtime().compareTo(sdf.format(calendar.getTime())) > 0;
            }
        }
        return false;
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
                if (System.currentTimeMillis() - now > 30000) {
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
        return tryLock(key, value, 30L, TimeUnit.SECONDS);
    }

    public boolean tryLock(String key, String value, Long time, TimeUnit timeUnit) {
        ValueOperations operations = redisTemplate.opsForValue();
        if (operations.setIfAbsent(key, value, time, timeUnit)) {
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
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Long nowTime = System.currentTimeMillis();
            for (CorgiActivity activity : activityList) {
                Long height = activity.getHeight();
                Long width = activity.getWidth();
                if (!CollectionUtils.isEmpty(activity.getPics()) && activity.getPics().size() == 1
                        && (height == null || width == null)) {
                    String picUrl = activity.getPics().get(0).getPicUrl();
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    height = picInfo.getHeight();
                    width = picInfo.getWidth();
                }
                Long likeCount = activity.getLikeCount();
                if(likeCount == null) {
                    likeCount = corgiLikeService.countActivityLike(activity.getId());
                }
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), userId);
                //List<ActivityLike> activityLikes = corgiLikeService.getFollowUser(userId, activity.getId());
                Long commentCount = corgiCommentService.countActivityComment(activity.getId());

                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initSize(height, width);
                detail.setTimeShow(TimeUtil.buildTimeText(detail.getCreateTime(), nowTime, sdf));
                detail.setHasLike(hasLike);
                detail.setLikeCount(likeCount);
                //detail.setLikeUsers(activityLikes);
                detail.setCommentCount(commentCount);

                if (!CorgiActivity.CAT_BUSINESS.equals(activity.getCategory()) && activity.getUserId() != null && !activity.getUserId().startsWith("B")) {
                    UserDetail userDetail = corgiUserService.getUserDetailBasic(activity.getUserId());
                    if (userDetail != null) {
                        detail.setUserDetail(userDetail);
                    }
                }
                if (barProfile != null) {
                    detail.setBarDetail(barProfile);
                } else {
                    detail.setBarDetail(corgiBarService.getBarProfile(detail.getUserId()));
                }
                detailList.add(detail);
            }
        }
        return detailList;
    }

    public UserDetail checkUserDetail(UserDetail detail, String userId) {
        if (detail == null) {
            return null;
        }
        if (AliyunGreenService.CHECK.equals(detail.getAvatarCheckStatus()) && userId.equals(detail.getUserId())) {
            CheckPic pic = corgiPicService.getCheckPicByDataId(detail.getAvatarDataId());
            if (pic != null) {
                detail.setAvatar(pic.getPicUrl());
            }
        }
        return detail;
    }
}
