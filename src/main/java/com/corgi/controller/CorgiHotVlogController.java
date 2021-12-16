package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.VlogDetail;
import com.corgi.service.AliyunGreenService;
import com.corgi.user.api.*;
import com.corgi.user.entity.CorgiVlog;
import com.corgi.user.entity.CorgiVlogHot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("hot_vlog")
public class CorgiHotVlogController extends BaseController {
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiPicService corgiPicService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiActivityService corgiActivityService;

    @GetMapping("list")
    public JsonResult listHot(@RequestParam(required = false, name = "status") String status, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiVlogHot hot = new CorgiVlogHot();
        hot.setType(CorgiVlogHot.TYPE.MANUAL);
        if (!StringUtils.isEmpty(status)) {
            hot.setStatus(status);
        }
        List<CorgiVlogHot> hots = corgiVlogService.getHotVlog(hot, page, pageSize);
        List<VlogDetail> details = new ArrayList<>();
        for (CorgiVlogHot hot1 : hots) {
            details.add(buildDetail(hot1));
        }
        return new JsonResult(details);
    }

    @GetMapping("count")
    public JsonResult countHot(@RequestParam(required = false, name = "status") String status) {
        CorgiVlogHot hot = new CorgiVlogHot();
        hot.setType(CorgiVlogHot.TYPE.MANUAL);
        if (!StringUtils.isEmpty(status)) {
            hot.setStatus(status);
        }
        return new JsonResult(corgiVlogService.countHotVlog(hot));
    }

    @PostMapping("update")
    public JsonResult updateHot(@RequestBody CorgiVlogHot corgiVlogHot) {
        corgiVlogHot.setViewCount(null);
        corgiVlogHot.setLikeCount(null);
        corgiVlogHot.setActivityId(null);
        corgiVlogService.updateHotVlog(corgiVlogHot);
        return new JsonResult();
    }

    @PostMapping("add")
    public JsonResult addHot(@RequestBody CorgiVlogHot corgiVlogHot) {
        corgiVlogHot.setViewCount(null);
        corgiVlogHot.setLikeCount(null);
        if (corgiVlogHot.getExpectView() == null || corgiVlogHot.getExpectView() <= 0) {
            corgiVlogHot.setExpectView(3000);
        }
        corgiVlogHot.setType(CorgiVlogHot.TYPE.MANUAL);
        corgiVlogService.addHotVlog(corgiVlogHot);
        corgiActivityService.updateByColumn(corgiVlogHot.getActivityId(), "checkStatus", "good");
        return new JsonResult();
    }

    private VlogDetail buildDetail(CorgiVlogHot hot) {
        VlogDetail detail = new VlogDetail();
        detail.setActivityId(hot.getActivityId());
        detail.setId(hot.getId());
        detail.setLikeCount(hot.getLikeCount());
        if (hot.getViewCount() != null) {
            detail.setViewCount(hot.getViewCount().longValue());
        }
        detail.setExpectView(hot.getExpectView());
        detail.setCtime(hot.getCtime());
        detail.setStatus(hot.getStatus());
        CorgiActivity activity = corgiActivityFeedService.getActivityById(hot.getActivityId());
        activity.setPics(corgiPicService.getActivityPic(activity.getId()));
        detail.setActivityDetail(activity);
        return detail;
    }
}
