package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("activity")
public class CorgiActivityController extends BaseController {
    @Reference
    private CorgiActivityService corgiActivityService;

    @GetMapping("add_activity")
    public JsonResult addActivity() {
        CorgiActivity activity = new CorgiActivity();
        Random random = new Random();
        activity.setLng(random.nextDouble());
        activity.setLat(random.nextDouble());
        activity.setPics(Arrays.asList("awegaweg", "awega", "awieow/ajie"));
        activity = corgiActivityService.addCorgiActivity(activity);
        return new JsonResult(activity);
    }

    @GetMapping("get_range_activity")
    public JsonResult getRangeActivity(@RequestParam("lng") double lng, @RequestParam("lat") double lat, @RequestParam("range") double range) {
        List<CorgiActivity> activityList = corgiActivityService.getCorgiActivityByRange(lng, lat, range);
        return new JsonResult(activityList);
    }
}
