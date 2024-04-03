package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("wechat")
public class WechatController extends BaseController {
    @Reference
    private CorgiToolService corgiToolService;
    @Reference
    private CorgiUserService corgiUserService;


    @GetMapping("getCorgiId")
    public JsonResult getCorgi(@RequestParam("wechatId") String wechatId) {
        //corgiToolService.getIdByWechatId(wechatId)
        return new JsonResult();
    }

}
