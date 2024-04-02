package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.RequestUtil;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.*;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.AsyncTaskService;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import com.corgi.user.enums.MerchandiseEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("external")
public class ThirdPartyController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiOrderService corgiOrderService;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private AsyncTaskService asyncTaskService;

    public static final String URL = "https://corgi-pic.oss-cn-beijing.aliyuncs.com/share/character/%s.png?x-oss-process=style/zip";

    @GetMapping("get_invited_bonus")
    public JsonResult getInvitedBonus(@RequestParam("userId") String userId) {
        MerchandiseEnum e = MerchandiseEnum.BONUS_SUBSCRIBE;
        List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                .userId(userId)
                .merchId(e.getCode())
                .start(0)
                .size(20)
                .build());
        List<HashMap<String, String>> results = new ArrayList<>();
        for (CorgiUserGoods g : goods) {
            HashMap<String, String> result = new HashMap<>();
            result.put("title", e.getTitle());
            result.put("desc", e.getDesc());
            result.put("ctime", g.getCtime());
            results.add(result);
        }
        return new JsonResult(results);
    }

    @GetMapping("get_invited_schedule")
    public JsonResult getInvitedSchedule(@RequestParam("userId") String userId) {
        Integer total = corgiOrderService.countInvited(userId, "");
        Integer count = total % 10;
        if (total > 120) {
            count = 10;
        }
        HashMap<String, Integer> result = new HashMap<>();
        result.put("count", count);
        result.put("threshold", 10);
        result.put("per", count * 10);
        return new JsonResult(result);
    }


    @GetMapping("recommend_user")
    public JsonResult getRecommendUser(@RequestParam("userId") String userId) {
        List<UserDetail> result = new ArrayList<>();
        String key = "recommend_user-" + userId;
        List<String> userIds = redisTemplate.opsForList().range(key, 0, -1);
        if (!CollectionUtils.isEmpty(userIds)) {
            for (String userId1 : userIds) {
                UserDetail detail = corgiUserService.getUserDetailBasic(userId1);
                if (detail != null) {
                    result.add(detail);
                }
                if (result.size() >= 8) {
                    break;
                }
            }
            return new JsonResult(result);
        }

        asyncTaskService.initRecommendUser(userId);
        ActivityBillboard query = new ActivityBillboard();
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        query.setDate(date);
        query.setCtime(date);
        List<ActivityBillboard> activityBillboards = corgiBillboardService.getAllActivityBillboard(query);
        if (activityBillboards != null) {
            for (ActivityBillboard billboard : activityBillboards) {
                UserDetail detail = corgiUserService.getUserDetailBasic(billboard.getUserId());
                if (detail != null && !detail.getUserId().equals(userId)) {
                    result.add(detail);
                }
                if (result.size() >= 8) {
                    break;
                }
            }
        }
        return new JsonResult(result);
    }

    @GetMapping("get_top_9")
    public JsonResult getTop9(@RequestParam("telNo") String telNo) {
        UserLogin login = new UserLogin();
        login.setTelNo(telNo);
        login = corgiUserService.login(login);
        if (login == null || StringUtils.isEmpty(login.getUserId())) {
            return new JsonResult();
        }
        UserDetail userDetail = corgiUserService.getUserDetailBasic(login.getUserId());
        if (userDetail == null) {
            return new JsonResult();
        }
        ActivityQuery query = new ActivityQuery();
        query.setStartTime("2022-01-01");
        query.setUserId(login.getUserId());
        query.setPageSize(9);
        List<String> activityIds = corgiUserActivityService.queryHotActivity(query);
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<ActivityPic> picUrls = new ArrayList<>();
        for (CorgiActivity activity : activities) {
            if (StringUtils.isEmpty(activity.getCoverUrl())) {
                if (!CollectionUtils.isEmpty(activity.getPics())) {
                    picUrls.add(convertPic(activity.getPics().get(0)));
                }
            } else {
                ActivityPic pic = new ActivityPic();
                pic.setPicUrl(activity.getCoverUrl() + "?x-oss-process=image/auto-orient,1/resize,m_fill,w_500,h_500/quality,q_90");
                picUrls.add(pic);
            }
        }
        CorgiActivityDetail activity = new CorgiActivityDetail();
        activity.setPics(picUrls);
        activity.setUserDetail(userDetail);
        userDetail.setCtime("2022-01-01");
        activity.setLikeCount(corgiLikeService.countLikeByUser(userDetail));
        return new JsonResult(activity);
    }

    @GetMapping("get_character_pic")
    public JsonResult getPic(@RequestParam(required = false, name = "answer") String character) {
        if (StringUtils.isEmpty(character) || character.length() < 4) {
            return new JsonResult(String.format(URL, character + ""));
        }
        String type = character.substring(0, 4);
        return new JsonResult(String.format(URL, type));
    }

    @GetMapping("count")
    public JsonResult count(@RequestParam("user") String user) {
        String lockKey = "count_" + user;
        corgiUtilService.lock(lockKey);
        try {
            corgiToolService.countUserNumber(user);
        } finally {
            corgiUtilService.unlock(lockKey);
        }
        return new JsonResult();
    }

    @GetMapping("/check_nickname")
    public JsonResult checkNickname(@RequestParam("nickname") String nickname) {
        if (com.alibaba.dubbo.common.utils.StringUtils.isEmpty(nickname)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "昵称为空");
        }
        log.info(aliyunGreenService.checkText(nickname) + "");
        return new JsonResult();
    }

    @GetMapping("get_user_activity")
    public JsonResult getMyRunningActivity(@RequestParam("userId") String userId) {
        ActivityQuery query = new ActivityQuery();
        query.setUserId(userId);
        query.setLoginUserId("");
        query.setPage(1);
        query.setPageSize(10);
        query.setCategory(CorgiActivity.CAT_IMAGE);
        List<CorgiActivity> activities = corgiActivityService.getFeedActivity(query);
        return new JsonResult(convertDetail(activities));
    }

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList) {
        List<com.corgi.entity.CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Long nowTime = System.currentTimeMillis();
            Iterator<CorgiActivity> it = activityList.iterator();
            while (it.hasNext()) {
                CorgiActivity activity = it.next();
                if (activity == null || activity.getUserId() == null || (("fail".equals(activity.getCheckStatus()) || "check".equals(activity.getCheckStatus())))) {
                    it.remove();
                    continue;
                }
                if (!CorgiActivity.CAT_IMAGE.equals(activity.getCategory()) || CollectionUtils.isEmpty(activity.getPics())) {
                    it.remove();
                    continue;
                }
                activity.setCoverUrl(activity.getPics().get(0).getPicUrl());
                if (AliyunGreenService.NOT_GOOD.equals(activity.getCheckStatus())) {
                    it.remove();
                    continue;
                }
                activity.setCurrentTime(now);
                com.corgi.entity.CorgiActivityDetail detail = new com.corgi.entity.CorgiActivityDetail(activity);
                detail.setTimeShow(TimeUtil.buildTimeText(detail.getCreateTime(), nowTime, sdf));
                detailList.add(detail);
                if (detailList.size() > 5) {
                    break;
                }
            }
        }
        return detailList;
    }

    private ActivityPic convertPic(ActivityPic pic) {
        String picUrl = pic.getPicUrl();
        if (!StringUtils.isEmpty(picUrl)) {
            picUrl += "?x-oss-process=style/top9";
            pic.setPicUrl(picUrl);
        }
        return pic;
    }

}
