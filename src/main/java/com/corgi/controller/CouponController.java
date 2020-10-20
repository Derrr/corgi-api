package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.ActivityBillboard;
import com.corgi.entity.BarActivityDetail;
import com.corgi.user.api.CorgiBillboardService;
import com.corgi.user.api.CorgiCouponService;
import com.corgi.user.api.CorgiPicService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.entity.CorgiCoupon;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("coupon")
public class CouponController extends BaseController {
    @Reference
    private CorgiCouponService corgiCouponService;
    @Reference
    private CorgiActivityService corgiActivityService;

    @PostMapping("add_coupon")
    public JsonResult addCoupon(@RequestBody CorgiCoupon coupon) {
        coupon.setBarId(getUserId());
        corgiCouponService.addCoupon(coupon);
        return new JsonResult();
    }

    @PostMapping("update_coupon")
    public JsonResult updateCoupon(@RequestBody CorgiCoupon coupon) {
        if (hasUserId()) {
            coupon.setBarId(getUserId());
        }
        corgiCouponService.updateCoupon(coupon);
        return new JsonResult();
    }

    @GetMapping("get_coupon")
    public JsonResult getCoupon(@RequestParam(required = false, name = "status") String status) {
        return new JsonResult(corgiCouponService.getCoupon(getUserId(), status));
    }

    @GetMapping("delete_coupon")
    public JsonResult deleteCoupon(@RequestParam("id") Integer id) {
        corgiCouponService.deleteCoupon(id, getUserId());
        return new JsonResult();
    }

    @GetMapping("get_activity_coupon")
    public JsonResult getActivityCoupon(@RequestParam("activityId") String activityId, @RequestParam(required = false, name = "status") String status) {
        return new JsonResult(corgiCouponService.getActivityCoupon(activityId, status));
    }

    @PostMapping("update_activity_coupon")
    public JsonResult updateActivityCoupon(@RequestParam BarActivityDetail barActivityDetail) {
        String activityId = barActivityDetail.getId();
        List<CorgiActivity> activityList = corgiActivityService.getActivityByIds(Arrays.asList(activityId));
        if (CollectionUtils.isEmpty(activityList)) {
            return new JsonResult();
        }
        CorgiActivity activity = activityList.get(0);
        if (!getUserId().equals(activity.getUserId())) {
            return new JsonResult();
        }
        corgiCouponService.deleteActivityCoupon(activityId);
        if (barActivityDetail.getCoupons() != null) {
            for (CorgiCoupon corgiCoupon : barActivityDetail.getCoupons()) {
                corgiCouponService.addActivityCoupon(activityId, corgiCoupon.getId());
            }
        }
        return new JsonResult();
    }
}
