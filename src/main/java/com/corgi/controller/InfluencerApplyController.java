package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.ActivityBillboard;
import com.corgi.user.entity.InfluencerApply;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("influencer")
public class InfluencerApplyController extends BaseController {
    @Reference
    private CorgiInfluencerApplyService corgiInfluencerApplyService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private MQService mqService;

    @PostMapping("apply")
    public JsonResult apply(@RequestBody InfluencerApply apply) {
        UserDetail userDetail = corgiUserService.getUserDetailBasic(getUserId());
        apply.setUserId(userDetail.getUserId());
        apply.setAvatar(userDetail.getAvatar());
        apply.setNickname(userDetail.getNicknameDataId());
        corgiInfluencerApplyService.addApply(apply);
        return new JsonResult();
    }

    @GetMapping("get_status")
    public JsonResult getStatus() {
        InfluencerApply query = new InfluencerApply();
        query.setUserId(getUserId());
        List<InfluencerApply> applies = corgiInfluencerApplyService.getApplies(query, 1, 1);
        HashMap result = new HashMap();
        result.put("status", 200);
        if (!CollectionUtils.isEmpty(applies)) {
            if ("applied".equals(applies.get(0).getStatus())) {
                result.put("wechat", applies.get(0).getWechat());
                result.put("status", 300);
                return new JsonResult(result, "审核中");
            }
            if ("pass".equals(applies.get(0).getStatus())) {
                result.put("status", 400);
                return new JsonResult(result, "已通过");
            }
        }
        return new JsonResult(result);
    }

    @GetMapping("get_applies")
    public JsonResult getApplies(@RequestParam("status") String status,
                                 @RequestParam(name = "userId",required = false, defaultValue = "") String userId,
                                 @RequestParam(name = "nickname",required = false, defaultValue = "") String nickname,
                                 @RequestParam(name = "wechat",required = false, defaultValue = "") String wechat,
                                 @RequestParam("page") Integer page,
                                 @RequestParam("size") Integer size) {
        InfluencerApply query = new InfluencerApply();
        query.setStatus(status);
        query.setUserId(userId);
        query.setNickname(nickname);
        query.setWechat(wechat);
        List<InfluencerApply> applies = corgiInfluencerApplyService.getApplies(query, page, size);
        Integer count = corgiInfluencerApplyService.countApplies(query);
        HashMap<String, Object> result = new HashMap<>();
        result.put("applies", applies);
        result.put("count", count);
        return new JsonResult(result);
    }

    @PostMapping("update_apply")
    public JsonResult updateApply(@RequestBody InfluencerApply apply) {
        corgiInfluencerApplyService.updateApply(apply);
        if (StringUtils.isEmpty(apply.getWechat())) {
            if ("pass".equals(apply.getStatus())) {
                UserDetail update = new UserDetail();
                update.setUserId(apply.getUserId());
                update.setAvatarStatus("influencer");
                corgiUserService.updateDetail(update);
                mqService.sendAdminMessage(apply.getUserId(), "您申请的天菜创始人已通过审核，24小时内运营小伙伴将会拉您入群，请留意微信消息");
            }
            if ("fail".equals(apply.getStatus())) {
                UserDetail update = new UserDetail();
                update.setUserId(apply.getUserId());
                update.setAvatarStatus("");
                corgiUserService.updateDetail(update);
                mqService.sendAdminMessage(apply.getUserId(), "很抱歉，您的天菜创始人申请未通过审核");
            }
        }
        return new JsonResult();
    }
}
