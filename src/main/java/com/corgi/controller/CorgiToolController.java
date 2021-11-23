package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.CorgiConstants;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.messages.PushMessage;
import com.corgi.entity.*;
import com.corgi.entity.tool.Hashtag;
import com.corgi.entity.tool.Topic;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.CorgiUtilService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("tool")
public class CorgiToolController extends BaseController {
    public static final String URL = "https://corgi-pic.oss-cn-beijing.aliyuncs.com/share/character/%s.png?x-oss-process=style/zip";

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
    @Reference
    private CorgiBlacklistService corgiBlacklistService;
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiSoundService corgiSoundService;
    @Reference
    private CorgiShareService corgiShareService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private MQService mqService;

    public static final String ACTIVITY_TASK = "activity";
    public static final String USER_TASK = "user";
    public static final String VERSION_KEY = "corgi_version";


    @GetMapping("query_user")
    public JsonResult queryUser(UserDetail userDetail, @RequestParam(required = false, name = "loginUserId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (hasUserId()) {
            userId = getUserId();
        }
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
        if (StringUtils.isEmpty(activity.getStatus())) {
            activity.setStatus(CorgiActivity.NOT_DELETED);
        }
        List<CorgiActivity> activityList = corgiActivityService.searchCorgiActivity(activity, page, pageSize);
        return new JsonResult(activityList);
    }

    @GetMapping("count_activity")
    public JsonResult countActivity(CorgiActivity activity) {
        if (StringUtils.isEmpty(activity.getStatus())) {
            activity.setStatus(CorgiActivity.NOT_DELETED);
        }
        long count = corgiActivityService.countCorgiActivity(activity);
        return new JsonResult(count);
    }


    @GetMapping("get_tags")
    public JsonResult getTags() {
        List<String> tags = corgiToolService.getTags();
        return new JsonResult(tags);
    }

    @GetMapping("check_pic")
    public JsonResult checkPic(@RequestParam("picUrl") String picUrl) {
        CorgiPic pic = new CorgiPic();
        pic.setPicUrl(picUrl);
        List<CorgiPic> results = (List<CorgiPic>) aliyunGreenService.checkPic(Arrays.asList(pic), getUserId(), "share");
        if (CollectionUtils.isEmpty(results)) {
            return new JsonResult("check");
        }
        return new JsonResult(results.get(0).getStatus());
    }

    @GetMapping("get_interests")
    public JsonResult getInterests(@RequestParam("category") String category) {
        List<String> interests = corgiToolService.getInterestsByCategory(category);
        return new JsonResult(interests);
    }

    @GetMapping("get_topics")
    public JsonResult getTopics() {
        List<CorgiTopic> topics = corgiToolService.searchTopic(null, "release");
        return new JsonResult(topics);
    }

    @GetMapping("get_hashtags")
    public JsonResult getHashtags() {
        List<CorgiHashtag> hashtags = corgiToolService.searchHashtag(null, "release");
        return new JsonResult(hashtags);
    }

    @GetMapping("search_topics")
    public JsonResult getAllTopics(@RequestParam(required = false, name = "status") String status, @RequestParam(required = false, name = "topic") String key) {
        List<CorgiTopic> topics = corgiToolService.searchTopic(key, status);
        List<Topic> result = new ArrayList<>();
        for (CorgiTopic corgiTopic : topics) {
            CorgiVlog countResult = corgiVlogService.countByTopic(corgiTopic.getTopicId());
            Topic topic = new Topic();
            BeanUtils.copyProperties(corgiTopic, topic);
            topic.initCount(countResult);
            result.add(topic);
        }
        return new JsonResult(result);
    }

    @GetMapping("search_hashtags")
    public JsonResult getAllHashtags(@RequestParam(required = false, name = "status") String status, @RequestParam(required = false, name = "hashtagName") String hashtagName) {
        List<CorgiHashtag> hashtags = corgiToolService.searchHashtag(hashtagName, status);
        List<Hashtag> result = new ArrayList<>();
        for (CorgiHashtag corgiHashtag : hashtags) {
            CorgiVlog countResult = corgiVlogService.countByHashtag(corgiHashtag.getHashtagId(), "real");
            Hashtag hashtag = new Hashtag();
            BeanUtils.copyProperties(corgiHashtag, hashtag);
            hashtag.initCount(countResult);
            result.add(hashtag);
        }
        return new JsonResult(result);
    }

    @GetMapping("get_activity_types")
    public JsonResult getActivityTypes() {
        List<String> types = corgiToolService.getActivityTypes();
        types.add("其他");
        return new JsonResult(types);
    }

    @GetMapping("get_date_types")
    public JsonResult getDateTypes() {
        List<DateType> types = corgiToolService.getDateTypes();
        return new JsonResult(types);
    }

    @GetMapping("add_topic")
    public JsonResult addTopic(CorgiTopic topic) {
        corgiToolService.addTopic(topic);
        return new JsonResult();
    }

    @GetMapping("add_hashtag")
    public JsonResult addHashtag(CorgiHashtag hashtag) {
        corgiToolService.addHashtag(hashtag);
        return new JsonResult();
    }

    @GetMapping("update_topic")
    public JsonResult updateTopic(CorgiTopic topic) {
        corgiToolService.updateTopic(topic);
        return new JsonResult();
    }

    @GetMapping("update_hashtag")
    public JsonResult updateHashtag(CorgiHashtag hashtag) {
        corgiToolService.updateHashtag(hashtag);
        return new JsonResult();
    }

    @PostMapping("update_activity_topic")
    public JsonResult upadteActivityTopic(@RequestBody CorgiActivityDetail corgiActivity) {
        log.info("id:{} ", corgiActivity.getId());
        corgiToolService.updateActivityTopic(corgiActivity.getId(), corgiActivity.getTopics());
        corgiActivityService.updateByColumn(corgiActivity.getId(), "topics", String.join(",", corgiActivity.getTopics()));
        return new JsonResult();
    }

    @PostMapping("update_activity_hashtag")
    public JsonResult upadteActivityHashtag(@RequestBody CorgiActivityDetail corgiActivity) {
        log.info("id:{} ", corgiActivity.getId());
        corgiToolService.updateActivityHashtag(corgiActivity.getId(), corgiActivity.getHashtags());
        corgiActivityService.updateByColumn(corgiActivity.getId(), "hashtags", String.join(",", corgiActivity.getHashtags()));
        return new JsonResult();
    }

    @GetMapping("sticky_top")
    public JsonResult stickyTop(@RequestParam("activityId") String activityId) {
        corgiToolService.updateActivityTopicWeight(activityId, 1);
        return new JsonResult();
    }

    @GetMapping("undo_sticky_top")
    public JsonResult undoStickyTop(@RequestParam("activityId") String activityId) {
        corgiToolService.updateActivityTopicWeight(activityId, 0);
        return new JsonResult();
    }

    @GetMapping("get_check_sound")
    public JsonResult getCheckSound(@RequestParam(required = false, name = "status", defaultValue = "") String
                                            status, @RequestParam("page") int page, @RequestParam("pageSize") int size) {
        List<CorgiSound> checkSound = corgiSoundService.getCheckSound(status, page, size);
        return new JsonResult(checkSound);
    }

    @GetMapping("count_check_sound")
    public JsonResult countCheckSound(@RequestParam(required = false, name = "status", defaultValue = "") String
                                              status) {
        long count = corgiSoundService.countCheckSound(status);
        return new JsonResult(count);
    }

    @GetMapping("pass_sound")
    public JsonResult passSound(CorgiSound corgiSound) {
        corgiSoundService.passCheckSound(corgiSound);
        return new JsonResult();
    }

    @GetMapping("refuse_sound")
    public JsonResult refuseSound(CorgiSound corgiSound) {
        corgiSoundService.failCheckSound(corgiSound);
        return new JsonResult();
    }

    @GetMapping("count_check_pic")
    public JsonResult countCheckPic(@RequestParam(required = false, name = "status", defaultValue = "") String
                                            status,
                                    @RequestParam(required = false, name = "type", defaultValue = "") String type,
                                    @RequestParam(required = false, name = "userId") String userId) {
        long count = corgiPicService.countCheckPic(status, type, userId);
        return new JsonResult(count);
    }

    @GetMapping("get_check_pic")
    public JsonResult getCheckPic(@RequestParam(required = false, name = "userId") String
                                          userId, @RequestParam(required = false, name = "status", defaultValue = "") String status,
                                  @RequestParam("page") int page, @RequestParam("pageSize") int size,
                                  @RequestParam(required = false, name = "type", defaultValue = "") String type) {
        List<CheckPic> checkPics = corgiPicService.getCheckPic(userId, status, type, page, size);
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

    @GetMapping("no_face_pic")
    public JsonResult noFacePic(CheckPic checkPic) {
        String result = corgiPicService.noFaceCheckPic(checkPic);
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
    public JsonResult getStatistics(@RequestParam("type") String type, @RequestParam("startDate") String
            startDate, @RequestParam("endDate") String endDate) {
        List<HashMap> statistics = null;
        if (CorgiStatistic.ACTIVITY_TYPE.equals(type) || CorgiStatistic.PUBLISH.equals(type) || CorgiStatistic.USER_CITY.equals(type)) {
            statistics = corgiStatisticService.getList(type, startDate, endDate);
        } else {
            statistics = corgiStatisticService.getMap(type, startDate, endDate);
        }
        return new JsonResult(statistics);
    }

    @GetMapping("sum_statistics")
    public JsonResult sumStatistics(@RequestParam("type") String
                                            type, @RequestParam(required = false, name = "beginDate") String
                                            beginDate, @RequestParam(required = false, name = "endDate") String endDate) {
        long sum = corgiStatisticService.sumCount(type, beginDate, endDate);
        return new JsonResult(sum);
    }

    @GetMapping("get_city_area")
    public JsonResult getCityArea(@RequestParam(required = false, name = "city") String city) {
        if (StringUtils.isEmpty(city)) {
            return new JsonResult(new ArrayList<>());
        }
        List<CorgiArea> corgiAreas = corgiAreaService.getAreaByCity(city);
        return new JsonResult(corgiAreas);
    }

    @GetMapping("get_character_pic")
    public JsonResult getPic(@RequestParam(required = false, name = "answer") String character) {
        if (StringUtils.isEmpty(character) || character.length() < 4) {
            return new JsonResult(String.format(URL, character + ""));
        }
        String type = character.substring(0, 4);
        return new JsonResult(String.format(URL, type));
    }

    @PostMapping("push_message")
    public JsonResult pushMessage(@RequestBody PushMessage pushMessage) {
        rabbitTemplate.convertAndSend(CorgiQueueName.PUSH_MESSAGE_QUEUE, pushMessage);
        return new JsonResult();
    }

    @GetMapping("agree_nickname")
    public JsonResult agreeNickname(@RequestParam("userId") String
                                            userId, @RequestParam(required = false, name = "nickname", defaultValue = "") String nickname) {
        if (!StringUtils.isEmpty(nickname)) {
            String result = corgiUserService.updateUserNickname(userId, nickname, "");
            if (!CorgiConstants.SUCCESS.equals(result)) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, result);
            }
        }
        return new JsonResult();
    }

    @GetMapping("agree_desc")
    public JsonResult agreeDesc(@RequestParam("userId") String
                                        userId, @RequestParam(required = false, name = "desc", defaultValue = "") String desc) {
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

    @GetMapping("fail_user")
    public JsonResult failUser(@RequestParam("userId") String userId) {
        UserDetail detail = new UserDetail();
        detail.setUserId(userId);
        detail.setCheckStatus(AliyunGreenService.FAIL);
        corgiUserService.updateDetail(detail);
        return new JsonResult();
    }

    @GetMapping("agree_title")
    public JsonResult agreeTitle(@RequestParam("activityId") String
                                         activityId, @RequestParam(required = false, name = "title", defaultValue = "") String title) {
        if (!StringUtils.isEmpty(title)) {
            corgiActivityService.updateByColumn(activityId, "title", title);
        }
        return new JsonResult();
    }

    @GetMapping("agree_type")
    public JsonResult agreeType(@RequestParam("activityId") String
                                        activityId, @RequestParam(required = false, name = "type", defaultValue = "") String type) {
        if (!StringUtils.isEmpty(type)) {
            corgiActivityService.updateByColumn(activityId, "activityType", type);
        }
        return new JsonResult();
    }

    @GetMapping("agree_content")
    public JsonResult agreeContent(@RequestParam("activityId") String
                                           activityId, @RequestParam(required = false, name = "content", defaultValue = "") String content) {
        if (!StringUtils.isEmpty(content)) {
            corgiActivityService.updateByColumn(activityId, "content", content);
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
        corgiUserActivityService.deleteActivityCreator(activityId);
        corgiUserActivityService.deleteActivity(activityId);
        return new JsonResult();
    }


    @GetMapping("agree_activity")
    public JsonResult agreeActivity(@RequestParam("activityId") String activityId) {
        corgiActivityService.updateByColumn(activityId, "checkStatus", AliyunGreenService.PASS);
        corgiUserActivityService.changeActivityCreator(activityId, "normal");
        return new JsonResult();
    }

    @GetMapping("fail_activity")
    public JsonResult failActivity(@RequestParam("activityId") String activityId) {
        corgiActivityService.updateByColumn(activityId, "checkStatus", AliyunGreenService.FAIL);
        return new JsonResult();
    }

    @GetMapping("downgrade_activity")
    public JsonResult downgradeActivity(@RequestParam("activityId") String activityId) {
        corgiActivityService.updateByColumn(activityId, "checkStatus", AliyunGreenService.NOT_GOOD);
        return new JsonResult();
    }

    @GetMapping("count_task")
    public JsonResult countTask(@RequestParam("type") String type) {
        long count = 0;
        switch (type) {
            case ACTIVITY_TASK:
                CorgiActivity corgiActivity = new CorgiActivity();
                corgiActivity.setCheckStatus(AliyunGreenService.CHECK);
                corgiActivity.setStatus(CorgiActivity.NOT_DELETED);
                corgiActivity.setCategory(CorgiActivity.CAT_IMAGE);
                count = corgiActivityService.countCorgiActivity(corgiActivity);
                break;
            case USER_TASK:
                UserDetail userDetail = new UserDetail();
                userDetail.setCheckStatus(AliyunGreenService.CHECK);
                count = corgiUserService.countUsers(userDetail);
                break;
        }
        return new JsonResult(count);
    }


    @GetMapping("get_city")
    public JsonResult getCity() {
        List<String> city = corgiAreaService.getCity();
        return new JsonResult(city);
    }

    @GetMapping("get_user_stay")
    public JsonResult getUserStay(@RequestParam("startDate") String startDate, @RequestParam("endDate") String
            endDate, @RequestParam("stayCount") String stayCount) {
        List<HashMap> userStays = corgiStatisticService.getUserStay(startDate, endDate, stayCount);
        return new JsonResult(userStays);
    }

    @GetMapping("get_user_trace")
    public JsonResult getUserTrace(@RequestParam("startDate") String startDate, @RequestParam("endDate") String
            endDate) {
        List<HashMap> traces = corgiStatisticService.getUserTraceSum(startDate, endDate);
        return new JsonResult(traces);
    }

    @GetMapping("add_character")
    public JsonResult addCharacter(@RequestParam("openId") String openId, @RequestParam("character") String
            character) {
        corgiStatisticService.addCharacter(openId, character);
        return new JsonResult();
    }

    @GetMapping("get_version")
    public JsonResult getVersion(@RequestParam(required = false, name = "type", defaultValue = "") String type) {
        return new JsonResult(redisTemplate.opsForHash().entries(VERSION_KEY + type));
    }

    @PostMapping("update_version")
    public JsonResult updateVersion(@RequestBody HashMap version) {
        String type = "";
        if (version.get("type") != null) {
            type = version.get("type").toString();
        }
        log.info("version:{}", version);
        redisTemplate.opsForHash().putAll(VERSION_KEY + type, version);
        return new JsonResult();
    }

    @PostMapping("report")
    public JsonResult report(@RequestBody CorgiReport report) {
        corgiBlacklistService.report(report);
        return new JsonResult();
    }

    @GetMapping("get_report")
    public JsonResult getReport(CorgiReport corgiReport, @RequestParam("page") Integer
            page, @RequestParam("pageSize") Integer pageSize) {
        return new JsonResult(corgiBlacklistService.getReport(corgiReport, page, pageSize));
    }

    @GetMapping("count_report")
    public JsonResult countReport(CorgiReport corgiReport) {
        return new JsonResult(corgiBlacklistService.countReport(corgiReport));
    }


    @GetMapping("update_report_status")
    public JsonResult updateReportStatus(@RequestParam("status") String status, @RequestParam("reportId") String
            reportId) {
        corgiBlacklistService.updateStatus(reportId, status);
        return new JsonResult();
    }

    @GetMapping("set_influencer")
    public JsonResult setInfluencer(@RequestParam("userId") String userId) {
        UserDetail userProfile = corgiUserService.getUserDetailBasic(userId);
        if (userProfile != null) {
            UserDetail userDetail = new UserDetail();
            userDetail.setAvatarStatus("influencer");
            userDetail.setUserId(userProfile.getUserId());
            corgiUserService.updateDetail(userDetail);
            corgiToolService.countUserNumber(userProfile.getNickname());
            mqService.sendInfluencerMessage(PushMessage.builder()
                    .targetUserId(userProfile.getUserId()).build());

        }
        return new JsonResult();
    }

    @GetMapping("clear_influencer")
    public JsonResult clearInfluencer(@RequestParam("userId") String userId) {
        UserDetail userProfile = corgiUserService.getUserDetailBasic(userId);
        if (userProfile != null) {
            UserDetail userDetail = new UserDetail();
            userDetail.setAvatarStatus("");
            userDetail.setUserId(userProfile.getUserId());
            corgiUserService.updateDetail(userDetail);
            corgiToolService.countUserNumber(userProfile.getNickname());
            mqService.sendInfluencerLeftMessage(PushMessage.builder().targetUserId(userProfile.getUserId()).build());
        }
        return new JsonResult();
    }


    @GetMapping("update_billboard")
    public JsonResult updateBillboard(@RequestParam(required = false, name = "from") String
                                              from, @RequestParam(required = false, name = "to") String to) {
        if (StringUtils.isEmpty(from) || StringUtils.isEmpty(to)) {
            return new JsonResult();
        }
        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        corgiBillboardService.updateBillboardByNickname(from, to, date);
        return new JsonResult();
    }

    @GetMapping("add_billboard")
    public JsonResult addBillboard(@RequestParam("userId") String userId, @RequestParam("date") String date) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);
        userProfile.setMatch(0.0);
        corgiBillboardService.addBillboard(userProfile, date, "user");
        return new JsonResult();
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

    @GetMapping("get_influencer")
    public JsonResult getInfluencer(@RequestParam(name = "userId", required = false) String userId) {
        if (StringUtils.isEmpty(userId)) {
            return new JsonResult(corgiToolService.getInfluencer());
        } else {
            return new JsonResult(corgiToolService.getCountByUser(userId));
        }
    }

    @GetMapping("get_match_factor")
    public JsonResult getMatchFactor(@RequestParam("table") String table) {
        List<HashMap> result = corgiUserMatchService.getMatchFactor(table);
        return new JsonResult(result);
    }

    @GetMapping("get_match_factor2")
    public JsonResult getMatchFactor2(@RequestParam("table") String table) {
        List<String> result = redisTemplate.opsForList().range("match_factor_" + table, 0, -1);
        return new JsonResult(result);
    }

    @GetMapping("set_match_factor2")
    public JsonResult setMatchFactor2(@RequestParam("value") List<String> value, @RequestParam("table") String
            table) {
        redisTemplate.delete("match_factor_" + table);
        redisTemplate.opsForList().rightPushAll("match_factor_" + table, value);
        return new JsonResult();
    }


    @GetMapping("update_match_factor")
    public JsonResult updateMatchFactor(@RequestParam Map<String, String> result) {
        String table = result.get("table");
        String cn1 = result.get("cn1");
        String cv1 = result.get("cv1");
        String cn2 = result.get("cn2");
        String cv2 = result.get("cv2");
        String match = result.get("match");
        corgiUserMatchService.updateMatchFactor(table, cn1, cv1, cn2, cv2, Integer.valueOf(match));
        return new JsonResult();
    }

    @GetMapping("refresh_all_match")
    public String refreshAllMatch() {
        int size = 1000;
        int start = 0;
        List<UserMatch> userMatchList;
        do {
            userMatchList = corgiUserMatchService.getUserMatchByPage(null, start, size);
            start += size;
            if (userMatchList != null) {
                for (UserMatch userMatch : userMatchList) {
                    String userId1 = userMatch.getUserId1();
                    String userId2 = userMatch.getUserId2();
                    String matchKey = CorgiConstants.getUserMatchKey(userId1, userId2);
                    String matchStr = redisTemplate.opsForValue().get(matchKey);
                    if (StringUtils.isEmpty(matchStr)) {
                        userMatch.setMatch(0);
                        corgiUserMatchService.updateMatch(userMatch);
                        continue;
                    }
                    Double match = corgiUserMatchService.calculateUserMatch(userId1, userId2);
                    redisTemplate.opsForValue().set(matchKey, match + "", 7, TimeUnit.DAYS);
                    userMatch.setMatch(match);
                    corgiUserMatchService.updateMatch(userMatch);
                }
            }
        } while (!CollectionUtils.isEmpty(userMatchList));
        return "success";
    }

    @GetMapping("get_match_ratio")
    public JsonResult getMatchRatio() {
        Map result = redisTemplate.opsForHash().entries(CorgiConstants.MATCH_FACTOR);
        return new JsonResult(result);
    }

    @GetMapping("set_match_ratio")
    public JsonResult setMatchRatio(@RequestParam Map factors) {
        log.info("factors..." + factors);
        redisTemplate.opsForHash().putAll(CorgiConstants.MATCH_FACTOR, factors);
        return new JsonResult();
    }

    @GetMapping("get_top_9")
    public JsonResult getTop9(@RequestParam("userId") String userId) {
        ActivityQuery query = new ActivityQuery();
        query.setStartTime("2021-01-01");
        query.setUserId(userId);
        query.setPageSize(9);
        List<String> activityIds = corgiUserActivityService.queryHotActivity(query);
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        List<String> picUrls = new ArrayList<>();
        for (CorgiActivity activity : activities) {
            if (StringUtils.isEmpty(activity.getCoverUrl())) {
                if (!CollectionUtils.isEmpty(activity.getPics())) {
                    picUrls.add(activity.getPics().get(0).getPicUrl());
                }
            } else {
                picUrls.add(activity.getCoverUrl());
            }
        }
        return new JsonResult(picUrls);
    }

    @GetMapping("get_share_token")
    public JsonResult getShareToken(@RequestParam("type") String type, @RequestParam(required = false, name = "sourceId", defaultValue = "-") String activityId) {
        return new JsonResult(corgiShareService.getShareToken(getUserId(), type, activityId));
    }

    @GetMapping("view_token")
    public JsonResult viewToken(@RequestParam("token") String token) {
        corgiShareService.viewShare(token);
        return new JsonResult();
    }

    @GetMapping("test")
    public JsonResult test() {
        CorgiReport corgiReport = new CorgiReport();
        corgiReport.setReportUserId("1");
        corgiReport.setReportUserName("嗷嗷");
        corgiReport.setAccuseId("17");
        corgiReport.setAccuseType("用户");
        corgiReport.setReason("dwaegwg");
        corgiReport.setDesc("描述");
        corgiReport.setPics(Arrays.asList("daseg", "gawiego"));
        corgiBlacklistService.report(corgiReport);
        return new JsonResult();
    }
}
