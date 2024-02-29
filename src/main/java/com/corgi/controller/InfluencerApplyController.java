package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.user.api.*;
import com.corgi.user.entity.ActivityBillboard;
import com.corgi.user.entity.InfluencerApply;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
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
        if (!CollectionUtils.isEmpty(applies)) {
            if("审核中".equals(applies.get(0).getStatus())){
                return new JsonResult(300,"审核中");
            }
            if("通过".equals(applies.get(0).getStatus())){
                return new JsonResult(400,"已通过");
            }
        }
        return new JsonResult();
    }

    @GetMapping("get_applies")
    public JsonResult getApplies(@RequestParam("status") String status, @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        InfluencerApply query = new InfluencerApply();
        query.setStatus(status);
        List<InfluencerApply> applies = corgiInfluencerApplyService.getApplies(query, page, size);
        Integer count = corgiInfluencerApplyService.countApplies(query);
        HashMap<String, Object> result = new HashMap<>();
        result.put("applies", applies);
        result.put("count", count);
        return new JsonResult(result);
    }

    @GetMapping("update_apply")
    public JsonResult updateApply(@RequestBody InfluencerApply apply) {
        corgiInfluencerApplyService.updateApply(apply);
        return new JsonResult();
    }
}
