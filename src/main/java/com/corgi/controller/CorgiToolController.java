package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiConstants;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.CheckPic;
import com.corgi.entity.CorgiArea;
import com.corgi.entity.CorgiStatistic;
import com.corgi.entity.CorgiTopic;
import com.corgi.service.AliyunGreenService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("tool")
public class CorgiToolController extends BaseController {
    public static final String URL = "https://corgi-pic.oss-cn-beijing.aliyuncs.com/share/character/%s.png";

    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiStatisticService corgiStatisticService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiAreaService corgiAreaService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @GetMapping("query_user")
    public JsonResult queryUser(UserDetail userDetail, @RequestParam(required = false, name = "loginUserId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<UserProfile> profiles = corgiUserService.searchUsers(userDetail, userId, page, pageSize);
        return new JsonResult(profiles);
    }

    @GetMapping("count_user")
    public JsonResult countUser(UserDetail userDetail) {
        long count = corgiUserService.countUsers(userDetail);
        return new JsonResult(count);
    }

    @GetMapping("query_activity")
    public JsonResult queryActivity(CorgiActivity activity, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(activity, page, pageSize);
        return new JsonResult(activityList);
    }

    @GetMapping("count_activity")
    public JsonResult countActivity(CorgiActivity activity) {
        long count = corgiActivityService.countCorgiActivity(activity);
        return new JsonResult(count);
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
    public JsonResult getTopics(@RequestParam(required = false, name = "status") String status, @RequestParam(required = false, name = "text") String text) {
        List<CorgiTopic> topics = corgiToolService.searchTopic(text, status);
        return new JsonResult(topics);
    }

    @GetMapping("add_topic")
    public JsonResult addTopic(CorgiTopic topic) {
        corgiToolService.addTopic(topic);
        return new JsonResult();
    }

    @GetMapping("update_topic")
    public JsonResult upadteTopic(CorgiTopic topic) {
        corgiToolService.updateTopic(topic);
        return new JsonResult();
    }

    @GetMapping("count_check_pic")
    public JsonResult countCheck(@RequestParam(required = false, name = "status", defaultValue = "") String status,
                                 @RequestParam(required = false, name = "type", defaultValue = "") String type) {
        long count = corgiPicService.countCheckPic(status, type);
        return new JsonResult(count);
    }

    @GetMapping("get_check_pic")
    public JsonResult getCheck(@RequestParam(required = false, name = "status", defaultValue = "") String status, @RequestParam("page") int page, @RequestParam("pageSize") int size, @RequestParam(required = false, name = "type", defaultValue = "") String type) {
        List<CheckPic> checkPics = corgiPicService.getCheckPic(status, type, page, size);
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
        List<HashMap> statistics = null;
        if (CorgiStatistic.ACTIVITY_TYPE.equals(type) || CorgiStatistic.USER_CITY.equals(type) || CorgiStatistic.PUBLISH.equals(type)) {
            statistics = corgiStatisticService.getList(type, startDate, endDate);
        } else {
            statistics = corgiStatisticService.getMap(type, startDate, endDate);
        }
        return new JsonResult(statistics);
    }

    @GetMapping("sum_statistics")
    public JsonResult sumStatistics(@RequestParam("type") String type, @RequestParam(required = false, name = "beginDate") String beginDate, @RequestParam(required = false, name = "endDate") String endDate) {
        long sum = corgiStatisticService.sumCount(type, beginDate, endDate);
        return new JsonResult(sum);
    }

    @GetMapping("get_city_area")
    public JsonResult getCityArea(@RequestParam("city") String city) {
        List<CorgiArea> corgiAreas = corgiAreaService.getAreaByCity(city);
        return new JsonResult(corgiAreas);
    }

    @GetMapping("get_character_pic")
    public JsonResult getPic(@RequestParam("answer") String character) {
        String type = character.substring(0, 4);
        return new JsonResult(String.format(URL, type));
    }

    @PostMapping("push_message")
    public JsonResult pushMessage(@RequestBody PushMessage pushMessage) {
        rabbitTemplate.convertAndSend(CorgiQueueName.PUSH_MESSAGE_QUEUE, pushMessage);
        return new JsonResult();
    }

    @GetMapping("agree_nickname")
    public JsonResult agreeNickname(@RequestParam("userId") String userId, @RequestParam(required = false, name = "nickname", defaultValue = "") String nickname) {
        if (!StringUtils.isEmpty(nickname)) {
            String result = corgiUserService.updateUserNickname(userId, nickname, nickname);
            if (!CorgiConstants.SUCCESS.equals(result)) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, result);
            }
        }
        return new JsonResult();
    }

    @GetMapping("agree_desc")
    public JsonResult agreeDesc(@RequestParam("userId") String userId, @RequestParam(required = false, name = "desc", defaultValue = "") String desc) {
        if (!StringUtils.isEmpty(desc)) {
            UserDetail userDetail = new UserDetail();
            userDetail.setDesc(desc);
            userDetail.setUserId(userId);
            String result = corgiUserService.updateDetail(userDetail);
            if (!CorgiConstants.SUCCESS.equals(result)) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, result);
            }
        }
        return new JsonResult();
    }

    @GetMapping("agree_user")
    public JsonResult agreeUser(@RequestParam("userId") String userId) {
        UserDetail detail = new UserDetail();
        detail.setUserId(userId);
        detail.setCheckStatus(AliyunGreenService.PASS);
        corgiUserService.updateDetail(detail);
        return new JsonResult();
    }

    @GetMapping("agree_title")
    public JsonResult agreeTitle(@RequestParam("activityId") String activityId, @RequestParam(required = false, name = "title", defaultValue = "") String title) {
        if (!StringUtils.isEmpty(title)) {
            corgiActivityService.updateByColumnn(activityId, "title", title);
        }
        return new JsonResult();
    }

    @GetMapping("agree_content")
    public JsonResult agreeContent(@RequestParam("activityId") String activityId, @RequestParam(required = false, name = "content", defaultValue = "") String content) {
        if (!StringUtils.isEmpty(content)) {
            corgiActivityService.updateByColumnn(activityId, "content", content);
        }
        return new JsonResult();
    }


    @GetMapping("delete_user")
    public JsonResult deleteUser(@RequestParam("userId") String userId) {
        corgiUserService.deleteUser(userId);
        corgiActivityService.deleteUserActivity(userId);
        return new JsonResult();
    }

    @GetMapping("remove_activity")
    public JsonResult removeActivity(@RequestParam("activityId") String activityId) {
        corgiActivityService.removeActivity(activityId);
        corgiUserActivityService.deleteActivity(activityId);
        return new JsonResult();
    }

    @GetMapping("agree_activity")
    public JsonResult agreeActivity(@RequestParam("activityId") String activityId) {
        corgiActivityService.updateByColumnn(activityId, "checkStatus", AliyunGreenService.PASS);
        return new JsonResult();
    }


    @GetMapping("get_city")
    public JsonResult getCity() {
        List<String> city = corgiAreaService.getCity();
        return new JsonResult(city);
    }

    @GetMapping("get_user_stay")
    public JsonResult getUserStay(@RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate, @RequestParam("stayCount") String stayCount) {
        List<HashMap> userStays = corgiStatisticService.getUserStay(startDate, endDate, stayCount);
        log.info("userStays:{}", userStays);
        return new JsonResult(userStays);
    }

    @GetMapping("get_user_trace")
    public JsonResult getUserTrace(@RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate) {
        List<HashMap> traces = corgiStatisticService.getUserTraceSum(startDate, endDate);
        return new JsonResult(traces);
    }

    @GetMapping("add_character")
    public JsonResult addCharacter(@RequestParam("openId") String openId, @RequestParam("character") String character) {
        corgiStatisticService.addCharacter(openId, character);
        return new JsonResult();
    }

    @GetMapping("test")
    public JsonResult test() {
        return new JsonResult();
    }
}
