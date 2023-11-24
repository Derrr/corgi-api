package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.concurrent.*;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class EasemobService {
    @Value("${easemob.orgname}")
    private String orgName;
    @Value("${easemob.appname}")
    private String appName;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String HOST = "https://a1.easemob.com/";

    private static final String USER_URL = "/users";
    private static final String METAUSER_URL = "/metadata/user";
    private static final String TOKEN_URL = "/token";

    private static final String TOKEN_KEY = "Easemob-token";

    private static ThreadPoolExecutor executorService = new ThreadPoolExecutor(4, 4, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());

    @Autowired
    private CorgiUtilService corgiUtilService;

    public void registerUser(String userId) {
        executorService.execute(() -> {
            String url = HOST + orgName + "/" + appName + USER_URL;
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Accept", "application/json;charset=UTF-8");
            headers.put("Content-Type", "application/json");
            HashMap user = new HashMap();
            user.put("username", "corgi" + userId);
            user.put("password", "2S64aWcgnEYNP7US");
            String result = corgiUtilService.postJson(url, user, headers);
            log.info("注册结果：" + result);
        });
    }

    public void refreshUser(UserDetail detail) {
        HashMap user = new HashMap();
        user.put("nickname", detail.getNickname());
        if (!"check".equals(detail.getAvatarCheckStatus())) {
            user.put("avatarurl", detail.getAvatar());
        }
        user.put("ext", System.currentTimeMillis() + "");
        String token = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (StringUtils.isEmpty(token)) {
            token = this.getToken();
        }
        String finalToken = token;
        String url = HOST + orgName + "/" + appName + METAUSER_URL + "/corgi" + detail.getUserId();
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("Authorization", "Bearer " + finalToken);
        String result = corgiUtilService.putJson(url, user, headers);
        log.info("刷新结果：" + result);
    }

    public void deleteUser(String userId) {
        String token = redisTemplate.opsForValue().get(TOKEN_KEY);
        if (StringUtils.isEmpty(token)) {
            token = this.getToken();
        }
        String finalToken = token;
        executorService.execute(() -> {
            String url = HOST + orgName + "/" + appName + USER_URL + "/corgi" + userId;
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + finalToken);
            String result = corgiUtilService.deleteJson(url, headers);
            log.info("删除结果：" + result);
        });
    }

    public String getToken() {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json;charset=UTF-8");
        headers.put("Content-Type", "application/json");
        String url = HOST + orgName + "/" + appName + TOKEN_URL;
        HashMap body = new HashMap();
        body.put("grant_type", "client_credentials");
        body.put("client_id", "YXA6NW6WhxTlSd6PW28d8s2geQ");
        body.put("client_secret", "YXA6bXC8NAPVUHKlxTlhCSSZOVwyiAQ");
        body.put("ttl", 24 * 3600);
        String result = corgiUtilService.postJson(url, body, headers);
        log.info("获取Token结果：" + result);
        JSONObject tokenObj = JSONObject.parseObject(result);
        String token = tokenObj.getString("access_token");
        redisTemplate.opsForValue().set(TOKEN_KEY, token, 20l, TimeUnit.HOURS);
        return token;
    }
}
