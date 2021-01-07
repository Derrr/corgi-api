package com.corgi.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
    private static final String HOST = "https://a1.easemob.com/";
    private static final String REGISTER_URL = "/users";

    private static ThreadPoolExecutor executorService = new ThreadPoolExecutor(4, 4, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>());

    @Autowired
    private CorgiUtilService corgiUtilService;

    public void registerUser(String userId) {
        executorService.execute(() -> {
            String url = HOST + orgName + "/" + appName + REGISTER_URL;
            HashMap user = new HashMap();
            user.put("username", "corgi" + userId);
            user.put("password", "2S64aWcgnEYNP7US");
            String result = corgiUtilService.postJson(url, user);
            log.info("注册结果：" + result);
        });
    }
}
