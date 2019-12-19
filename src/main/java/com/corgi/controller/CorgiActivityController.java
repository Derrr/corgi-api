package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.user.api.CorgiUserMatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("activity")
public class CorgiActivityController extends BaseController {
    @Reference
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserMatchService corgiUserMatchService;

    private static Comparator<CorgiActivityDetail> detailComparator = (o1, o2) -> o2.getMatch().compareTo(o1.getMatch());

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        activity = corgiActivityService.addCorgiActivity(activity);
        return new JsonResult(activity.getId());
    }

    @GetMapping("get_range_activity")
    public JsonResult getRangeActivity(@RequestParam("userId") String userId, @RequestParam("lng") double lng, @RequestParam("lat") double lat, @RequestParam("range") double range) {
        List<CorgiActivity> activityList = corgiActivityService.getCorgiActivityByRange(lng, lat, range);
        List<CorgiActivityDetail> detailList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(activityList)) {
            for (CorgiActivity activity : activityList) {
                double match = corgiUserMatchService.getUserMatch(userId, activity.getUserId());
                detailList.add(new CorgiActivityDetail(activity).initMatch(match));
            }
        }
        detailList.sort(detailComparator);
        return new JsonResult(detailList);
    }
}
