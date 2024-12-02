package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.entity.IncomeBillboardUser;
import com.corgi.entity.PicInfo;
import com.corgi.entity.tlx.TlxActivityList;
import com.corgi.entity.tlx.TlxUser;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("tlx_activity")
public class TlxActivityController extends BaseController {
    @Reference
    private TlxActivityService tlxActivityService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private MQService mqService;

    @GetMapping("get_by_id")
    public JsonResult getById(@RequestParam("id")String id){
        TlxActivity result = tlxActivityService.getActivity(id);
        return new JsonResult(result);
    }

    @GetMapping("get_by_city")
    public JsonResult getByCity(@RequestParam("city") String city, @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        TlxActivity query = new TlxActivity();
        query.setCity(city);
        query.setStatus("1");
        List<TlxActivity> tlxActivities = tlxActivityService.getActivityList(page, size, query);
        List<TlxActivityList> tlxActivityLists = new ArrayList<>();
        for (TlxActivity activity : tlxActivities) {
            TlxActivityList activityList = new TlxActivityList();
            BeanUtils.copyProperties(activity,activityList);
            activityList.setNickname("天狼星户外旅行");
            activityList.setAvatar("http://image.corgi.org.cn/default-avatar/WechatIMG148.jpg");
            tlxActivityLists.add(activityList);
        }
        return new JsonResult(tlxActivityLists);
    }

    @GetMapping("get_extra_info")
    public JsonResult getExtraInfo(@RequestParam("id") String id) {
        TlxUser tlx = new TlxUser();
        tlx.setUserInfo(tlxActivityService.getActivityUsers(id));
        tlx.setCount(tlxActivityService.countActivityUser(id));
        return new JsonResult(tlx);
    }

    @GetMapping("add_user")
    public JsonResult addUser(@RequestParam("id") String id) {
        Integer count = tlxActivityService.countUserActivity(id, getUserId());
        if (count < 1) {
            tlxActivityService.addActivityUser(id, getUserId());
            UserDetail detail = corgiUserService.getUserDetailBasic(getUserId());
            TlxActivity tlxActivity = tlxActivityService.getActivity(id);
            String message = "你的关注【" + detail.getNickname() + "】报名了【" + tlxActivity.getShorttitle() + "】，一起去看看吧~";
            for (int i = 1; i < 1000; i++) {
                List<UserProfile> profiles = corgiUserFollowService.getFollowedUserByPage(getUserId(), 0l, i, 200);
                if (CollectionUtils.isEmpty(profiles)) {
                    break;
                }
                for (UserProfile profile : profiles) {
                    PushMessage pushMessage = new PushMessage();
                    pushMessage.setSourceUserId("corgihelper");
                    pushMessage.setTargetUserId(profile.getUserId());
                    pushMessage.setMessage("您关注的人报名了活动");
                    HashMap<String, Object> extra = new HashMap<>();
                    extra.put("type", "907");
                    JSONArray content = new JSONArray();
                    content.add(new JSONObject().fluentPut("text", message));
//                    content.add(new JSONObject().fluentPut("text", "查看活动>")
//                            .fluentPut("url",id).fluentPut("urlType","19"));
                    extra.put("content", content);
                    extra.put("bottomText", "查看活动>");
                    extra.put("bottomUrlType", "19");
                    extra.put("bottomUrl", id);
                    pushMessage.setExtra(extra);
                    mqService.sendMessage(pushMessage);
                }
            }
        }
        return new JsonResult();
    }

    @GetMapping("delete_user")
    public JsonResult deleteUser(@RequestParam("id") String id) {
        tlxActivityService.deleteActivityUser(id, getUserId());
        return new JsonResult();
    }

}
