package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserLogin;
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
public class CorgiUserController extends BaseController {
    @Reference
    private CorgiUserService corgiUserService;

    @GetMapping("/login")
    public JsonResult register() {
        UserLogin userLogin = new UserLogin();
        userLogin.setTelNo("11222223343");
        userLogin.setImId("xxxzzxcocox");
        corgiUserService.login(userLogin);
        return new JsonResult(null);
    }

}
