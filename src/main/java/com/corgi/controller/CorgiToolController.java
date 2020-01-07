package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiStatistic;
import com.corgi.user.api.CorgiPicService;
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
    @Reference
    private CorgiPicService corgiPicService;

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

    @GetMapping("add_topic")
    public JsonResult addTopic(@RequestParam("topic") String topic) {
        corgiToolService.addTopic(topic);
        return new JsonResult();
    }

    @GetMapping("delete_topic")
    public JsonResult deleteTopic(@RequestParam("topic") String topic) {
        corgiToolService.deleteTopic(topic);
        return new JsonResult();
    }

    @GetMapping("get_statistic")
    public JsonResult getStatistic(@RequestParam("type") String type, @RequestParam("beginDate") String beginDate, @RequestParam("endDate") String endDate) {
        List<CorgiStatistic> statistics = corgiToolService.getCount(type, beginDate, endDate);
        return new JsonResult(statistics);
    }

    @GetMapping("count_check_pic")
    public JsonResult countCheck(@RequestParam(required = false, name = "status", defaultValue = "") String status) {
        long count = corgiPicService.countCheckPic(status);
        return new JsonResult(count);
    }

    @GetMapping("get_check_pic")
    public JsonResult getCheck(@RequestParam(required = false, name = "status", defaultValue = "") String status, @RequestParam("page") int page, @RequestParam("size") int size) {
        List<CheckPic> checkPics = corgiPicService.getCheckPic(status, page, size);
        return new JsonResult(checkPics);
    }

    @GetMapping("pass_pic")
    public JsonResult passPic(CheckPic checkPic) {
        String result = corgiPicService.passCheckPic(checkPic);
        if (!CorgiConstants.SUCCESS.equals(result)) {
            new JsonResult(Constants.PARAMETER_ERROR_CODE, result);
        }
        return new JsonResult();
    }

    @GetMapping("refuse_pic")
    public JsonResult refusePic(CheckPic checkPic) {
        String result = corgiPicService.failCheckPic(checkPic);
        if (!CorgiConstants.SUCCESS.equals(result)) {
            new JsonResult(Constants.PARAMETER_ERROR_CODE, result);
        }
        return new JsonResult();
    }

    @GetMapping("get_statistics")
    public JsonResult getStatistics(@RequestParam("type") String type, @RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate) {
        List<CorgiStatistic> statistics = corgiToolService.getCount(type, startDate, endDate);
        return new JsonResult(statistics);
    }

    @GetMapping("sum_statistics")
    public JsonResult sumStatistics(@RequestParam("type") String type) {
        long sum = corgiToolService.sumCount(type);
        return new JsonResult(sum);
    }
}
