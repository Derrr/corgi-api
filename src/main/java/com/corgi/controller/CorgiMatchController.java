package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.Matcher;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserMatchItem;
import com.corgi.user.entity.UserQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("match")
public class CorgiMatchController extends BaseController {
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private MQService mqService;

    @GetMapping("count_range")
    public JsonResult countRange(@RequestParam("lat") Double lat, @RequestParam("lng") Double lng) {
        UserQuery userQuery = new UserQuery();
        userQuery.setLat(lat);
        userQuery.setLng(lng);
        userQuery.setRange(200.0);
        userQuery.setUserId(getUserId());
        String key = "count_range_" + getUserId();
        String result = redisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(result)) {
            redisTemplate.opsForValue().set(key, corgiUserMatchService.countAllMatcher(userQuery) + "", 12l, TimeUnit.HOURS);
        }
        result = redisTemplate.opsForValue().get(key);
        return new JsonResult(result);
    }

    @PostMapping("get_matches")
    public JsonResult addActivity(@RequestBody UserQuery userQuery) {
        if (!hasUserId()) {
            return new JsonResult(0, Constants.MATCH_REMAIN_ERROR_CODE, "获取匹配人员失败");
        }
        userQuery.setUserId(getUserId());
        String freqKey = getUserId() + "_get_match_times";
        String suffix = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String dayFreqKey = freqKey.concat("_").concat(suffix);
        String expireDate = corgiUserService.getUserVipExpire(getUserId());
        Integer threshold = 50;
        if (!StringUtils.isEmpty(expireDate) && !"-".equals(expireDate)) {
            threshold = 200;
        } else {
            UserDetail detail = corgiUserService.getUserDetailBasic(getUserId());
            if ("influencer".equals(detail.getAvatarStatus())) {
                threshold = 200;
            }
        }
        Integer checkDayResult = this.checkDayFreq(dayFreqKey, threshold);
        if (checkDayResult < 1) {
            return new JsonResult(0, Constants.MATCH_REMAIN_ERROR_CODE, "今日匹配点击已达" + threshold + "次上限次数，请明日再来哦");
        }
        checkDayResult -= 1;
        if (checkDayResult < 0) {
            checkDayResult = 0;
        }
        String key = "matching-" + userQuery.getUserId();
        try {
            if (corgiUtilService.lock(key)) {
                List<UserMatchItem> items = corgiUserMatchService.getUserMatchItem(userQuery);
                return new JsonResult(items, checkDayResult + "");
            }
        } finally {
            corgiUtilService.unlock(key);
        }
        return new JsonResult(null, checkDayResult + "");
    }

    @PostMapping("desire")
    public JsonResult addActivity(@RequestBody Matcher matcher) {
        List<String> matchIds = matcher.getMatchIds();
        if (matchIds == null) {
            matchIds = new ArrayList<>();
        }
        HashMap<String, Object> extra = new HashMap<>();
        extra.put("userId", getUserId());
        extra.put("type", PushMessage.QUICK_MATCH_ACCEPT_TYPE);
        extra.put("greeting", matcher.getGreeting());
        extra.put("filterSource", matcher.getFilterSource());
        if ("1".equals(matcher.getType())) {
//            String key = "last_accept_" + getUserId();
//            List<String> lastAcceptList = redisTemplate.opsForList().range(key, 0, -1);
//            if (lastAcceptList == null) {
//                lastAcceptList = new ArrayList<>();
//            }
            for (String matchId : matchIds) {
//                    if (!lastAcceptList.contains(matchId)) {
                if (redisTemplate.opsForValue().setIfAbsent("acceptMatching_" + getUserId() + "-" + matchId, "1", 20l, TimeUnit.HOURS)) {
                    corgiUserMatchService.addUserMatch(getUserId(), matchId, "1");
                    mqService.sendMessage(PushMessage.builder()
                            .type(PushMessage.DEFAULT)
                            .sourceUserId(getUserId())
                            .targetUserId(matchId)
                            .message("匹配成功，快去聊聊吧")
                            .extra(extra)
                            .build());
                }
//            }
//        }
//                redisTemplate.delete(key);
//                redisTemplate.opsForList().rightPushAll(key, matchIds);
//                redisTemplate.expire(key, 20l, TimeUnit.HOURS);
            }
            return new JsonResult();
        }

        String key = "count_matching-" + getUserId();
        Integer result = 100;
        String matchKey = "last_match_" + getUserId();
        try {
            if (corgiUtilService.lock(key)) {
                String freqKey = getUserId() + "_match_times";
                //String checkResult = this.checkFreq(freqKey, 20);
//                if (!StringUtils.isEmpty(checkResult)) {
//                    return new JsonResult(0, Constants.MATCH_TIMES_ERROR_CODE, checkResult);
//                }
                String suffix = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String dayFreqKey = freqKey.concat("_").concat(suffix);
                //String checkDayResult = this.checkDayFreq(dayFreqKey, 100);
//                if (!StringUtils.isEmpty(checkResult)) {
//                    return new JsonResult(0, Constants.MATCH_REMAIN_ERROR_CODE, checkDayResult);
//                }
                Long times = redisTemplate.opsForList().size(dayFreqKey);
                result = 100 - times.intValue();
                if (result < 0) {
                    result = 0;
                }
//                List<UserMatchRemain> remains = corgiUserMatchService.countUserRemain(getUserId());
//                Integer remain = 0;
//                if (!CollectionUtils.isEmpty(remains)) {
//                    for (UserMatchRemain remain1 : remains) {
//                        remain += remain1.getRemain();
//                    }
//                }
//                result = remain - matchIds.size();
//                if (result < 0) {
//                    return new JsonResult(Constants.MATCH_REMAIN_ERROR_CODE, "今日匹配总数已达上限次数，请明日再来哦");
//                }
                extra.put("type", PushMessage.QUICK_MATCH_TYPE);
//                if (!CollectionUtils.isEmpty(remains) && !CollectionUtils.isEmpty(matchIds)) {

//                List<String> lastMatchList = redisTemplate.opsForList().range(matchKey, 0, -1);
//                redisTemplate.delete(matchKey);


//                if (lastMatchList == null) {
//                    lastMatchList = new ArrayList<>();
//                }
//                    int i = 0;
//                    for (UserMatchRemain remain1 : remains) {
//                        Integer size = remain1.getRemain();
//                        for (int j = size; j > 0; j--) {
//                            if (i >= matchIds.size()) {
//                                return new JsonResult(result);
//                            }
                for (String matchId : matchIds) {
//                    redisTemplate.opsForList().rightPush(matchKey, matchId);
//                    if (lastMatchList.contains(matchId)) {
//                        continue;
//                    }
                    //corgiUserMatchService.addUserMatch(getUserId(), matchId, remain1.getTradeNo());
                    if (redisTemplate.opsForValue().setIfAbsent(matchKey + "-" + matchId, System.currentTimeMillis() + "", 10l, TimeUnit.HOURS)) {
                        mqService.sendMessage(PushMessage.builder()
                                .type(PushMessage.DEFAULT)
                                .sourceUserId(getUserId())
                                .targetUserId(matchId)
                                .message("有一个小哥哥想和你匹配，要去聊聊吗？")
                                .extra(extra)
                                .build());
                    }
//                            i++;
//                        }
                }
//                }
            }
        } finally {
            if (redisTemplate.hasKey(matchKey)) {
                redisTemplate.expire(matchKey, 1L, TimeUnit.HOURS);
            }
            corgiUtilService.unlock(key);
        }
        return new

                JsonResult(result);

    }

    private Integer checkDayFreq(String dayFreqKey, Integer threshold) {
        boolean hasKey = redisTemplate.hasKey(dayFreqKey);
        Long times = redisTemplate.opsForList().size(dayFreqKey);
        while (times >= threshold) {
            return 0;
        }
        redisTemplate.opsForList().leftPush(dayFreqKey, System.currentTimeMillis() + "");
        if (!hasKey) {
            redisTemplate.expire(dayFreqKey, 1l, TimeUnit.DAYS);
        }
        return threshold - times.intValue();
    }

    private String checkFreq(String freqKey, Integer threshold) {
        Long now = System.currentTimeMillis();
        Long times = redisTemplate.opsForList().size(freqKey);
        while (times >= threshold) {
            String lastTime = redisTemplate.opsForList().rightPop(freqKey);
            try {
                if (now - Long.valueOf(lastTime) < 60000) {
                    redisTemplate.opsForList().rightPush(freqKey, lastTime);
                    return "匹配太频繁啦，请休息一分钟再来哦";
                }
            } catch (Exception e) {

            }
            times = redisTemplate.opsForList().size(freqKey);
        }
        redisTemplate.opsForList().leftPush(freqKey, now + "");
        redisTemplate.expire(freqKey, 2l, TimeUnit.MINUTES);
        return null;
    }
}
