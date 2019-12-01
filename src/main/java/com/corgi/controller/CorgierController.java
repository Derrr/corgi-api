package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.profile.service.CorgierProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("user")
public class CorgierController extends BaseController {
    @Reference
    private CorgierProfileService corgierProfileService;

    @GetMapping("/register")
    public JsonResult register() {
        corgierProfileService.register();
        return new JsonResult(null);
    }

}
