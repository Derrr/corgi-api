package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.entity.PicInfo;
import com.corgi.service.AliyunGreenService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("billboard")
public class BillboardController extends BaseController {
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiOrderService corgiOrderService;
    @Autowired
    private AliyunGreenService aliyunGreenService;

    @GetMapping("get_by_date")
    public JsonResult getByDate(@RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate) {
        return new JsonResult(corgiBillboardService.getBillboardByDate(startDate, endDate));
    }

    @GetMapping("update_by_nickname")
    public JsonResult updateByNickname(@RequestParam("from") String from, @RequestParam("to") String to, @RequestParam("date") String date) {
        corgiBillboardService.updateBillboardByNickname(from, to, date);
        return new JsonResult();
    }

    @GetMapping("update_order")
    public JsonResult updateOrder(@RequestParam("userId") String userId, @RequestParam("date") String date, @RequestParam("order") Integer order) {
        corgiBillboardService.updateBillboardOrder(userId, date, order);
        return new JsonResult();
    }

    @GetMapping("get_activity_billboard")
    public JsonResult getActivityBillboard() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String date = sdf.format(new Date());
        List<String> activityIds = corgiBillboardService.getActivityBillboard(date);
        List<CorgiActivity> activities = corgiActivityService.getActivityByIds(activityIds);
        return new JsonResult(buildActivity(activities));
    }

    @GetMapping("get_activity_billboard_by_date")
    public JsonResult getAllActivityBillboard(@RequestParam("startTime") String date, @RequestParam("endTime") String endTime) {
        ActivityBillboard activityBillboard = new ActivityBillboard();
        activityBillboard.setDate(date);
        activityBillboard.setCtime(endTime);
        List<ActivityBillboard> activityBillboards = corgiBillboardService.getAllActivityBillboard(activityBillboard);
        return new JsonResult(buildActivityBillboard(activityBillboards));
    }

    @GetMapping("add_activity_billboard")
    public JsonResult addActivityBillboard(@RequestParam("activityId") String activityId,
                                           @RequestParam("userId") String userId,
                                           @RequestParam("date") String date,
                                           @RequestParam("order") Integer order) {
        if (!hasUserId()) {
            ActivityBillboard activityBillboard = new ActivityBillboard();
            activityBillboard.setActivityId(activityId);
            activityBillboard.setUserId(userId);
            activityBillboard.setDate(date);
            activityBillboard.setOrder(order);
            corgiBillboardService.addActivityBillboard(activityBillboard);
        }
        return new JsonResult();
    }

    @GetMapping("update_activity_billboard")
    public JsonResult updateActivityBillboard(@RequestParam("oldActivityId") String oldActivityId,
                                              @RequestParam("activityId") String activityId,
                                              @RequestParam("date") String date,
                                              @RequestParam("order") Integer order) {
        if (!hasUserId()) {
            ActivityBillboard activityBillboard = new ActivityBillboard();
            activityBillboard.setActivityId(oldActivityId);
            activityBillboard.setDate(date);
            activityBillboard.setOrder(order);
            corgiBillboardService.deleteActivityBillboard(activityBillboard);
            activityBillboard.setActivityId(activityId);
            corgiBillboardService.addActivityBillboard(activityBillboard);
        }
        return new JsonResult();
    }

    @GetMapping("delete_activity_billboard")
    public JsonResult deleteActivityBillboard(@RequestParam("activityId") String activityId, @RequestParam("date") String date) {
        if (!hasUserId()) {
            ActivityBillboard activityBillboard = new ActivityBillboard();
            activityBillboard.setActivityId(activityId);
            activityBillboard.setDate(date);
            corgiBillboardService.deleteActivityBillboard(activityBillboard);
        }
        return new JsonResult();
    }

    private List<ActivityBillboardDetail> buildActivityBillboard(List<ActivityBillboard> billboards) {
        List<ActivityBillboardDetail> detailList = new ArrayList<>();
        for (ActivityBillboard billboard : billboards) {
            List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(billboard.getActivityId()));
            if (!CollectionUtils.isEmpty(corgiActivities)) {
                ActivityBillboardDetail detail = ActivityBillboardDetail.getResult(corgiActivities.get(0));
                detail.setDate(billboard.getDate());
                detail.setOnBoardCount(billboard.getCount());
                detail.setOrder(billboard.getOrder());
                detail.setLastOnboardDate(billboard.getCtime());
                detail.setUserDetail(corgiUserService.getUserDetailBasic(detail.getUserId()));
                detailList.add(detail);
            }
        }
        return detailList;
    }

    private List<CorgiActivityDetail> buildActivity(List<CorgiActivity> activityList) {

        List<CorgiActivityDetail> detailList = new ArrayList<>();
        String now = new SimpleDateFormat("yyyy/MM/dd HH:mm").format(new Date());
        if (!CollectionUtils.isEmpty(activityList)) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            Long nowTime = System.currentTimeMillis();
            Iterator<CorgiActivity> it = activityList.iterator();
            while (it.hasNext()) {
                CorgiActivity activity = it.next();
                if (activity == null || activity.getUserId() == null) {
                    it.remove();
                    continue;
                }
                if ("fail".equals(activity.getCheckStatus()) || (!(CorgiActivity.CAT_VIDEO.equals(activity.getCategory()) || CorgiActivity.CAT_TEXT.equals(activity.getCategory())) && CollectionUtils.isEmpty(activity.getPics()))) {
                    it.remove();
                    continue;
                }
                activity.setCurrentTime(now);
                Long height = activity.getHeight();
                Long width = activity.getWidth();

                if (!CollectionUtils.isEmpty(activity.getPics()) && (height == null || width == null)) {
                    String picUrl = activity.getPics().get(0).getPicUrl();
                    PicInfo picInfo = aliyunGreenService.getAliyunPicInfo(picUrl);
                    height = picInfo.getHeight();
                    width = picInfo.getWidth();
                }

                if (CorgiActivity.CAT_PAYING.equals(activity.getCategory())) {
                    if (activity.getCoverUrl() != null && !activity.getCoverUrl().contains("?x-oss-process") && StringUtils.isEmpty(activity.getVideoId())) {
                        activity.setCoverUrl(activity.getCoverUrl() + "?x-oss-process=style/fuzzyCover");
                    }
                    activity.setRefActivityPic(activity.getCoverUrl());
                    List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                            .traderId(activity.getUserId())
                            .goodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY)
                            .goodsId(activity.getId())
                            .start(0)
                            .size(3)
                            .build());
                    if (!CollectionUtils.isEmpty(goods) || activity.getUserId().equals(getUserId())) {
                        if (!activity.getUserId().equals(getUserId()) && (!hasUserId() || CollectionUtils.isEmpty(corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                                .userId(getUserId())
                                .traderId(activity.getUserId())
                                .goodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY)
                                .start(0)
                                .size(1)
                                .goodsId(activity.getId())
                                .build())))) {
                            activity.setPics(new ArrayList<>());
                            activity.setVideoUrl("");
                            activity.setStatus("unpay");
                        } else {
                            if (!CollectionUtils.isEmpty(activity.getPics())) {
                                String picUrl = activity.getPics().get(0).getPicUrl();
                                activity.setCoverUrl(picUrl);
                            } else {
                                activity.setCoverUrl(activity.getCoverUrl().replaceAll("\\?x-oss-process=style/fuzzyCover", ""));
                            }
                        }
                    } else {
                        activity.setPics(new ArrayList<>());
                        activity.setVideoUrl("");
                        activity.setStatus("unpay");
                    }
                }
                Long likeCount = activity.getLikeCount();
                if (likeCount == null) {
                    likeCount = corgiLikeService.countActivityLike(activity.getId());
                }
                Integer hasLike = corgiLikeService.countUserLike(activity.getId(), getUserId());
                CorgiActivityDetail detail = new CorgiActivityDetail(activity)
                        .initSize(height, width)
                        .initLikeCount(likeCount)
                        .hasLike(hasLike);
                detail.setTimeShow(TimeUtil.buildTimeText(detail.getCreateTime(), nowTime, sdf));
                if (!StringUtils.isEmpty(activity.getMerchId())) {
                    detail.setMerchandise(corgiOrderService.getMerchandiseById(activity.getMerchId(), getUserId()));
                }
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
