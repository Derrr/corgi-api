package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiInfluencerApplyService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.entity.ActivityBillboard;
import com.corgi.user.entity.InfluencerApply;
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

    @PostMapping("apply")
    public JsonResult apply(@RequestBody JSONObject content) {
        InfluencerApply apply = new InfluencerApply();
        apply.setContent(content.toJSONString());
        corgiInfluencerApplyService.addApply(apply);
        return new JsonResult();
    }

    @GetMapping("get_applies")
    public JsonResult getApplies(@RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        List<InfluencerApply> applies = corgiInfluencerApplyService.getApplies(page, size);
        List<JSONObject> result = new ArrayList<>();
        if (CollectionUtils.isEmpty(applies)) {
            for (InfluencerApply apply : applies) {
                if (StringUtils.isEmpty(apply.getContent())) {
                    continue;
                }
                try {
                    result.add(JSONObject.parseObject(apply.getContent()));
                } catch (Exception e) {
                    log.error("apply:{} ", apply.getContent());
                }
            }
        }
        return new JsonResult(result);
    }


}
