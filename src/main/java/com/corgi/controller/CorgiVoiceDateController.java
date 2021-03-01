package com.corgi.controller;

import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.entity.DateDetail;
import com.corgi.entity.UserDate;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("voice_date")
public class CorgiVoiceDateController extends BaseController {
    private static final String DATE_USER_OF = "voice_date_user_of_";
    private static final String DATE_RESPONSE = "voice_date_response_";
    private static final String DATED_USERS = "voice_dated_users_";
    private static final String TICKET = "voice_ticket_";
    private static final String PARK = "voice_date_park";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;

    @PostMapping("hunt")
    public JsonResult searchDate(@RequestBody UserDate userDate) {
        if (hasUserId()) {
            userDate.setUserId(getUserId());
        }
        if (StringUtils.isEmpty(userDate.getUserId()) || userDate.getLat() == null || userDate.getLng() == null) {
            return new JsonResult();
        }
        try {
            enterPark(userDate);
            for (int i = 0; i < 100; i++) {
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String takenId = checkTaken(userDate.getUserId());
                if (StringUtils.isNotEmpty(takenId)) {
                    if (hasTicket(userDate.getUserId()) && hitBack(takenId, userDate)) {
                        return match(takenId, userDate.getUserId());
                    }
                    clearTaken(userDate.getUserId());
                    if (!hasTicket(userDate.getUserId())) {
                        return new JsonResult();
                    }
                }

                String quarry = pick(userDate, 100);
                if (!hasTicket(userDate.getUserId())) {
                    return new JsonResult();
                }
                if (StringUtils.isEmpty(quarry)) {
                    continue;
                }
                if (flirt(quarry, userDate.getUserId())) {
                    takenId = waitTaken(userDate.getUserId());
                    if (!hasTicket(userDate.getUserId())) {
                        return reject(userDate.getUserId(), takenId);
                    } else if (quarry.equals(takenId)) {
                        return match(takenId, userDate.getUserId());
                    }
                    clearTaken(userDate.getUserId());
                }
            }
        } finally {
            leavePark(userDate.getUserId());
        }
        return new JsonResult();
    }

    @GetMapping("end_hunt")
    public JsonResult endHunt(@RequestParam(required = false, name = "userId") String userId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        leavePark(userId);
        return new JsonResult();
    }

    @GetMapping("get_response")
    public JsonResult getResponse(@RequestParam("dateId") String dateId, @RequestParam(required = false, name = "userId") String userId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        Map response = new HashMap();
        response.put("response", null);

        for (int i = 0; i < 100; i++) {
            response = redisTemplate.opsForHash().entries(DATE_RESPONSE.concat(dateId).concat(userId));
            if (response != null && StringUtils.isNotEmpty((String) response.get("response"))) {
                if (response.get("response").equals("refuse")) {
                    response.put("message", "");
                }
                return new JsonResult(response);
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
        }
        response.put("message", "小哥哥可能在忙哦 再换一个试试吧！");
        return new JsonResult(response);
    }


    @PostMapping("response")
    public JsonResult responseResult(@RequestBody HashMap<String, String> result) {
        String userId = result.get("userId");
        if (hasUserId()) {
            userId = getUserId();
        }
        String dateId = result.get("dateId");
        result.put("message", "刚刚拒绝了一名小可爱");
        redisTemplate.opsForHash().putAll(DATE_RESPONSE.concat(userId).concat(dateId), result);
        redisTemplate.expire(DATE_RESPONSE.concat(userId).concat(dateId), 10, TimeUnit.SECONDS);
        return new JsonResult(result);
    }

    private JsonResult reject(String userId, String dateId) {
        if (StringUtils.isNotEmpty(dateId)) {
            Map response = new HashMap();
            response.put("response", "refuse");
            response.put("message", "阿欧～有时候换个头像更容易遇到天菜哦，快去试试吧！");
            response.put("userId", userId);
            response.put("dateId", dateId);
            redisTemplate.expire(DATE_RESPONSE.concat(userId).concat(dateId), 10, TimeUnit.SECONDS);
        }
        return new JsonResult();
    }

    private void enterPark(UserDate userDate) {
        redisTemplate.opsForGeo().remove(PARK, userDate.getUserId());
        redisTemplate.opsForGeo().add(PARK, new Point(userDate.getLng(), userDate.getLat()), userDate.getUserId());
        redisTemplate.opsForValue().set(TICKET.concat(userDate.getUserId()), userDate.getUserId(), 60, TimeUnit.SECONDS);
        clearTaken(userDate.getUserId());
    }

    private void leavePark(String userId) {
        redisTemplate.opsForGeo().remove(PARK, userId);
        redisTemplate.delete(TICKET.concat(userId));
    }

    private boolean hasTicket(String userId) {
        return redisTemplate.hasKey(TICKET.concat(userId));
    }

    private String pick(UserDate userDate, int i) {
        if (!hasTicket(userDate.getUserId())) {
            return null;
        }
        Integer range = 50 * i;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = redisTemplate.opsForGeo().radius(PARK, new Circle(new Point(userDate.getLng(), userDate.getLat()), new Distance(range, Metrics.KILOMETERS)));
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> results = geoResults.getContent();
        log.info(" get size:{} ", results.size());
        if (results.size() > 0) {
            Map datedMap = redisTemplate.opsForHash().entries(DATED_USERS.concat(userDate.getUserId()));
            Long now = System.currentTimeMillis();
            for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results) {
                String pickId = geoResult.getContent().getName();
                log.info(" id:{} ", pickId);
                if (userDate.getUserId().equals(pickId)) {
                    continue;
                }
//                if (datedMap != null) {
//                    String time = (String) datedMap.get(pickId);
//                    if (time != null && now - Long.parseLong(time) < 4 * 3600 * 1000) {
//                        continue;
//                    } else if (time != null) {
//                        redisTemplate.opsForHash().delete(DATED_USERS.concat(userDate.getUserId()), pickId);
//                    }
//                }
                return pickId;
            }
        }
        return null;
    }

    private boolean flirt(String dateId, String userId) {
        ValueOperations<String, String> valueOperation = redisTemplate.opsForValue();
        String takenId = valueOperation.get(DATE_USER_OF.concat(dateId));
        if (userId.equals(takenId)) {
            return true;
        }
        return valueOperation.setIfAbsent(DATE_USER_OF.concat(dateId), userId, 30, TimeUnit.SECONDS);
    }

    private boolean hitBack(String takenId, UserDate userDate) {
        if (hasTicket(takenId)) {
            return flirt(takenId, userDate.getUserId());
        }
        return false;
    }

    private void clearTaken(String userId) {
        redisTemplate.delete(DATE_USER_OF.concat(userId));
    }

    private String waitTaken(String userId) {
        String takenId = null;
        for (int i = 0; i < 10; i++) {
            //若已经匹配，此时即使没有ticket也会立即返回匹配值，因为对方有可能已经显示匹配成功，这边需先匹配成功再取消
            if (hasTicket(userId) && StringUtils.isEmpty(takenId = checkTaken(userId))) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                break;
            }
        }
        return takenId;
    }

    private String checkTaken(String userId) {
        if (redisTemplate.hasKey(DATE_USER_OF.concat(userId))) {
            return redisTemplate.opsForValue().get(DATE_USER_OF.concat(userId));
        }
        return null;
    }

    private JsonResult match(String takenId, String loginUserId) {

        UserDetail userDetail = corgiUserService.getUserDetail(takenId, loginUserId);
        DateDetail dateDetail = new DateDetail();
        dateDetail.setImId(userDetail.getImId());
        dateDetail.setLat(userDetail.getLat());
        dateDetail.setLng(userDetail.getLng());
        dateDetail.setMatch(userDetail.getMatch());
        dateDetail.setNickname(userDetail.getNickname());
        dateDetail.setUserId(userDetail.getUserId());
        dateDetail.setAvatar(userDetail.getAvatar());
        dateDetail.setAvatarCheckStatus(userDetail.getAvatarCheckStatus());
        dateDetail.setUserPics(userDetail.getUserPics());

        List<Point> points = redisTemplate.opsForGeo().position(PARK, takenId);
        if (CollectionUtils.isNotEmpty(points) && points.get(0) != null) {
            Point point = points.get(0);
            dateDetail.setLat(point.getY());
            dateDetail.setLng(point.getX());
        }

        dateDetail.setIsFollowed(corgiUserFollowService.isFollowed(loginUserId, takenId));
        redisTemplate.opsForHash().put(DATED_USERS.concat(loginUserId), takenId, System.currentTimeMillis() + "");
        redisTemplate.expire(DATED_USERS.concat(loginUserId), 4, TimeUnit.HOURS);
        return new JsonResult(dateDetail);
    }

}

