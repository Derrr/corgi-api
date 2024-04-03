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

    @GetMapping("bind")
    public JsonResult bind(@RequestParam("wechatId") String wechatId, @RequestParam("corgiTel") String corgiTel) {
//        if (!"-2".equals(getUserId())) {
//            return new JsonResult();
//        }
//        UserDetail detail = new UserDetail();
//        detail.setTelNo(corgiTel);
//        List<UserProfile> profiles = corgiUserService.searchUsers(detail, null, 1, 1);
//        if (CollectionUtils.isEmpty(profiles) || profiles.get(0) == null) {
//            return new JsonResult(Constants.API_ERROR_CODE, "用户不存在");
//        }
//        corgiToolService.bindWechat(wechatId, profiles.get(0).getUserId());
//        return new JsonResult(profiles.get(0).getUserId());
        return new JsonResult();
    }

    @GetMapping("getCorgiId")
    public JsonResult getCorgi(@RequestParam("wechatId") String wechatId) {
        //corgiToolService.getIdByWechatId(wechatId)
        return new JsonResult();
    }

}
