package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.util.TimeUtil;
import com.corgi.entity.ActivityBillboardDetail;
import com.corgi.entity.CorgiActivityDetail;
import com.corgi.entity.IncomeBillboardUser;
import com.corgi.entity.PicInfo;
import com.corgi.entity.tlx.TlxUser;
import com.corgi.service.AliyunGreenService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("tlx_activity")
public class TlxActivityController extends BaseController {
    @Reference
    private TlxActivityService tlxActivityService;

    @GetMapping("get_by_city")
    public JsonResult getByCity(@RequestParam("city") String city, @RequestParam("page")Integer page, @RequestParam("size")Integer size) {
        TlxActivity query = new TlxActivity();
        query.setCity(city);
        query.setStatus("1");
        return new JsonResult(tlxActivityService.getActivityList(page,size,query));
    }

    @GetMapping("get_extra_info")
    public JsonResult getExtraInfo(@RequestParam("id")String id){
        TlxUser tlx = new TlxUser();
        tlx.setUserInfo(tlxActivityService.getActivityUsers(id));
        tlx.setCount(tlxActivityService.countActivityUser(id));
        return new JsonResult(tlx);
    }

    @GetMapping("add_user")
    public JsonResult addUser(@RequestParam("id")String id){
        Integer count = tlxActivityService.countUserActivity(id, getUserId());
        if(count < 1) {
            tlxActivityService.addActivityUser(id, getUserId());
        }
        return new JsonResult();
    }

    @GetMapping("delete_user")
    public JsonResult deleteUser(@RequestParam("id")String id){
        tlxActivityService.deleteActivityUser(id, getUserId());
        return new JsonResult();
    }

}
