package com.corgi.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CorgiUtilService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private ThreadLocal<String> value;

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
