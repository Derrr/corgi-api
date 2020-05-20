package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiBarService;
import com.corgi.user.entity.BarProfile;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("bar")
public class CorgiBarController extends BaseController {
    @Reference
    private CorgiBarService corgiBarService;

    @PostMapping("add_bar")
    public JsonResult addBar(@RequestBody BarProfile barProfile) {
        corgiBarService.addBarProfile(barProfile);
        return new JsonResult();
    }

    @PostMapping("update_bar")
    public JsonResult updateBar(@RequestBody BarProfile barProfile){
        corgiBarService.updateBarProfile(barProfile);
        return new JsonResult();
    }

    @GetMapping("get_bar_list")
    public JsonResult getBarList(@RequestParam(required = false,name = "status")String status){
        List<BarProfile> barProfiles = corgiBarService.getBarList(status);
        return new JsonResult(barProfiles);
    }

    @GetMapping("get_bar")
    public JsonResult getBar(@RequestParam(required = false,name = "barId")String barId){
        BarProfile barProfiles = corgiBarService.getBarProfile(barId);
        return new JsonResult(barProfiles);
    }

}

