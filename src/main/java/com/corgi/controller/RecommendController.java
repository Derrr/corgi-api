package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboard;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.api.CorgiUserRecommendService;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("recommend")
public class RecommendController extends BaseController {
    @Reference
    private CorgiUserRecommendService corgiUserRecommendService;

    @GetMapping("get_user")
    public JsonResult getUser(@RequestParam("city") String city, @RequestParam("size") Integer size) {
        List<UserProfile> result = corgiUserRecommendService.getRecUser(getUserId(), size);
        if (result.size() < size) {
            List<UserProfile> extraResult = corgiUserRecommendService.getInfluencerByCity(getUserId(),city,size - result.size());
            result.addAll(extraResult);
        }
        return new JsonResult(result);
    }


    @GetMapping("dislike")
    public JsonResult disLike(@RequestParam("userId") String userId) {
        corgiUserRecommendService.distLikeUser(getUserId(), userId);
        return new JsonResult();
    }

}
