package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboard;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.entity.PicInfo;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
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
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiShareService corgiShareService;
    @Reference
    private CorgiUserService corgiUserService;

    @GetMapping("get_user")
    public JsonResult getUser(@RequestParam("city") String city, @RequestParam("size") Integer size) {
        List<UserProfile> result = corgiUserRecommendService.getRecUser(getUserId(), size);
        if (result.size() < size) {
            List<UserProfile> extraResult = corgiUserRecommendService.getInfluencerByCity(getUserId(), city, size - result.size());
            result.addAll(extraResult);
        }
        return new JsonResult(result);
    }

    @GetMapping("get_city_image")
    public JsonResult getCityImage(@RequestParam("city") String city, @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        List<String> activityIds = corgiUserRecommendService.getCityRecommendImage(getUserId(), city, page, size);
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(activityIds);
        return new JsonResult(convertDetail(activityList));
    }

    @GetMapping("get_not_city_image")
    public JsonResult getNotCityImage(@RequestParam("city") String city, @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        List<String> activityIds = corgiUserRecommendService.getNotCityRecommendImage(getUserId(), city, page, size);
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(activityIds);
        return new JsonResult(convertDetail(activityList));
    }


    @GetMapping("dislike")
    public JsonResult disLike(@RequestParam("userId") String userId) {
        corgiUserRecommendService.distLikeUser(getUserId(), userId);
        return new JsonResult();
    }

    private List<CorgiActivityDetail> convertDetail(List<CorgiActivity> activityList) {
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            Iterator<CorgiActivity> it = activityList.iterator();
            while (it.hasNext()) {
                CorgiActivity activity = it.next();
                if ("fail".equals(activity.getCheckStatus())) {
                    it.remove();
                    continue;
                }
                activity.setCurrentTime(now);

                Long commentCount = corgiCommentService.countActivityComment(activity.getId());
                Long likeCount = corgiLikeService.countActivityLike(activity.getId());
                List<ActivityLike> users = corgiLikeService.getFollowUser(getUserId(), activity.getId());
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), getUserId());
                Integer shareCount = corgiShareService.countShare(activity.getId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initCommentCount(commentCount)
                        .initLikeCount(likeCount)
                        .initLikeUsers(users)
                        .hasLike(hasLike);

                detail.setShareCount(shareCount);
                if (!StringUtils.isEmpty(activity.getUserId())) {
                    UserDetail userDetail = corgiUserService.getUserDetail(activity.getUserId(), null);
                    detail.setUserDetail(userDetail);
                }

                detailList.add(detail);

            }
        }
        return detailList;
    }

}
