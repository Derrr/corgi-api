package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiToolService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("tool")
public class CorgiToolController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;

    @Reference
    private CorgiToolService corgiToolService;

    @Reference
    private CorgiActivityService corgiActivityService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("query_user")
    public JsonResult queryUser(UserDetail userDetail) {
        List<UserProfile> profiles = corgiUserService.searchUsers(userDetail);
        return new JsonResult(profiles);
    }

    @GetMapping("query_activity")
    public JsonResult queryActivity(CorgiActivity activity) {
        List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(activity);
        return new JsonResult(activityList);
    }


    @GetMapping("get_tags")
    public JsonResult getTags() {
        List<String> tags = corgiToolService.getTags();
        return new JsonResult(tags);
    }

    @GetMapping("get_interests")
    public JsonResult getInterests(@RequestParam("category") String category) {
        List<String> interests = corgiToolService.getInterestsByCategory(category);
        return new JsonResult(interests);
    }

    @GetMapping("get_topics")
    public JsonResult getTopics() {
        List<String> topics = corgiToolService.getTopics();
        return new JsonResult(topics);
    }

}
