package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.*;
import com.corgi.user.entity.CorgiVlog;
import com.corgi.user.entity.CorgiVlogHot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("list")
    public JsonResult listHot(@RequestParam(required = false, name = "status") String status, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiVlogHot hot = new CorgiVlogHot();
        hot.setType(CorgiVlogHot.TYPE.MANUAL);
        if (!StringUtils.isEmpty(status)) {
            hot.setStatus(status);
        }
        return new JsonResult(corgiVlogService.getHotVlog(hot, page, pageSize));
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
        corgiVlogHot.setType(CorgiVlogHot.TYPE.MANUAL);
        corgiVlogService.addHotVlog(corgiVlogHot);
        return new JsonResult();
    }
}
