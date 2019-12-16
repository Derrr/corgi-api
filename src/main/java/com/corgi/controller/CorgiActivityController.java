package com.corgi.controller;

import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import jdk.nashorn.internal.ir.annotations.Reference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("activity")
public class CorgiActivityController extends BaseController {
    @Reference
    private CorgiActivityService corgiActivityService;

    @PostMapping("add_activity")
    public JsonResult addActivity(@RequestBody CorgiActivity activity) {
        activity = new CorgiActivity();
        corgiActivityService.addCorgiActivity(activity);
        return new JsonResult();
    }
}
