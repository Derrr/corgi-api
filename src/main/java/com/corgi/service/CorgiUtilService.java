package com.corgi.service;

import com.alibaba.fastjson.JSONObject;
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

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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

    private ThreadLocal<String> value;

    @PostConstruct
    public void init(){
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
        ValueOperations operations = redisTemplate.opsForValue();
        if (operations.setIfAbsent(key, value, 30, TimeUnit.SECONDS)) {
            return true;
        }
        return false;
    }

    public void unlock(String key) {
        String id = redisTemplate.opsForValue().get(key);
        if (id != null && value.get().equals(id)) {
            redisTemplate.delete(key);
        }
    }
}
