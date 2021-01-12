package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.aliyun.oss.common.comm.ResponseMessage;
import com.corgi.activity.api.CorgiActivityFeedService;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.entity.BarActivityDetail;
import com.corgi.entity.VlogDetail;
import com.corgi.service.MQService;
import com.corgi.user.api.CorgiCouponService;
import com.corgi.user.api.CorgiFeedService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.api.CorgiVlogService;
import com.corgi.user.entity.CorgiCoupon;
import com.corgi.user.entity.CorgiFeed;
import com.corgi.user.entity.CorgiVlog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("feed")
public class CorgiFeedController extends BaseController {
    @Reference
    private CorgiFeedService corgiFeedService;
    @Reference
    private CorgiActivityFeedService corgiActivityFeedService;
    @Reference
    private CorgiVlogService corgiVlogService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiActivityService corgiActivityService;
    @Autowired
    private MQService mqService;

    @GetMapping("get_feeds")
    public JsonResult getFeeds() {
        List<String> feedIds = corgiFeedService.getUnviewFeed(getUserId());
        List<VlogDetail> details = new ArrayList<>();
        for (String feed : feedIds) {
            CorgiVlog vlog = corgiVlogService.getVlog(feed);
            VlogDetail vlogDetail = VlogDetail.createDetail(vlog);
            String userId = vlog.getUserId();
            vlogDetail.setUserDetail(corgiUserService.getUserDetail(userId, null));
            vlogDetail.setActivityDetail(corgiActivityFeedService.getActivityById(feed));
            details.add(vlogDetail);
            corgiFeedService.viewFeed(feed, getUserId());
        }
        mqService.refreshFeed(getUserId());
        return new JsonResult(details);
    }

    @PostMapping("add_view")
    public JsonResult addView(@RequestBody CorgiActivity corgiActivity) {
        corgiActivity.setCategory(CorgiActivity.CAT_VIDEO);
        CorgiActivity result = corgiActivityFeedService.addFeedActivity(corgiActivity);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        CorgiVlog corgiVlog = new CorgiVlog();
        corgiVlog.setActivityId(result.getId());
        corgiVlog.setUserId(corgiActivity.getUserId());
        corgiVlog.setType(CorgiVlog.TYPE.USER);
        corgiVlog.setStatus(CorgiVlog.STATUS.UNCHECK);
        if (StringUtils.isEmpty(corgiActivity.getCurrentTime())) {
            corgiVlog.setCtime(sdf.format(new Date()));
        } else {
            corgiVlog.setCtime(corgiActivity.getCurrentTime());
        }
        corgiVlogService.addVlog(corgiVlog);
        return new JsonResult(result.getId());
    }
}
