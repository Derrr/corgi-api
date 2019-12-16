package com.corgi.controller;

import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import jdk.nashorn.internal.ir.annotations.Reference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
        corgiActivityService.addCorgiActivity(activity);
        return new JsonResult();
    }
}
