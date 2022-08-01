package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.common.util.RequestUtil;
import com.corgi.user.api.CorgiOpenPageService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.CorgiOpenPage;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPosition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("open_page")
public class CorgiOpenPageController extends BaseController {

    @Reference
    private CorgiOpenPageService corgiOpenPageService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("add_open_page")
    public JsonResult addBanner(@RequestBody CorgiOpenPage corgiOpenPage) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiOpenPageService.addOpenPage(corgiOpenPage);
        return new JsonResult();
    }

    @PostMapping("update_open_page")
    public JsonResult updateBanner(@RequestBody CorgiOpenPage corgiOpenPage) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiOpenPageService.updateOpenPage(corgiOpenPage);
        return new JsonResult();
    }

    @GetMapping("delete_open_page")
    public JsonResult deleteBanner(@RequestParam("pageId") Integer pageId) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiOpenPageService.deleteOpenPage(pageId);
        return new JsonResult();
    }

    @GetMapping("list_open_page")
    public JsonResult listBanner(CorgiOpenPage corgiOpenPage) {
        return new JsonResult(corgiOpenPageService.listOpenPage(corgiOpenPage));
    }

    @GetMapping("get_open_page")
    public JsonResult getBanner(CorgiOpenPage corgiOpenPage) {
        List<CorgiOpenPage> result ;
//                = new ArrayList<>();
//        corgiOpenPage.setUrlType("12");
//        result.add(corgiOpenPage);
        if (hasVersion()) {
            UserDetail detail = corgiUserService.getUserDetailBasic(getUserId());
            SimpleDateFormat sdf = new SimpleDateFormat("/MM/dd");
            if (detail != null && !StringUtils.isEmpty(detail.getBirthday()) && detail.getBirthday().contains(sdf.format(new Date()))) {
                CorgiOpenPage page = new CorgiOpenPage();
                page.setUrl(getUserId());
                page.setPicUrl(detail.getAvatar());
                page.setPicType("birthday");
                page.setUrlType("4");
                page.setTitle(detail.getNickname());
                if (redisTemplate.opsForValue().setIfAbsent("birthday_" + getUserId() + "_" + page.getUrl(), System.currentTimeMillis() + "", 24L, TimeUnit.HOURS)) {
                    log.info("birthday self:{} ", getUserId());
                    return new JsonResult(Arrays.asList(page));
                }
            }
            result = corgiOpenPageService.getBirthdayOpenPage(getUserId());
            if (!CollectionUtils.isEmpty(result)) {
                for (CorgiOpenPage page : result) {
                    if (redisTemplate.opsForValue().setIfAbsent("birthday_" + getUserId() + "_" + page.getUrl(), System.currentTimeMillis() + "", 24L, TimeUnit.HOURS)) {
                        log.info("birthday from:{},to:{} ", page.getUrl(), getUserId());
                        return new JsonResult(Arrays.asList(page));
                    }
                }
            }
        }
        corgiOpenPage.setStatus(CorgiOpenPage.STATUS_ENABLE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        corgiOpenPage.setStartTime(sdf.format(new Date()));
        UserPosition userPosition = corgiUserService.getUserPosition(getUserId());
        if (userPosition != null && userPosition.getVersion() != null) {
            String version = userPosition.getVersion().replaceAll("android", "");
            if ("2.0.3".compareTo(version) > 0) {
                corgiOpenPage.setPicType("image");
            }
        }
        List<CorgiOpenPage> openPages = corgiOpenPageService.listOpenPage(corgiOpenPage);
        String province = corgiOpenPage.getProvince();
        if (StringUtils.isEmpty(province)) {
            province = userPosition.getProvince();
        }
        if (StringUtils.isEmpty(province) && !StringUtils.isEmpty(corgiOpenPage.getCity())) {
            province = corgiUserService.getProvince(corgiOpenPage.getCity());
        }
        if (!StringUtils.isEmpty(province)) {
            corgiOpenPage.setCity(province);
            openPages.addAll(corgiOpenPageService.listOpenPage(corgiOpenPage));
        }
        corgiOpenPage.setCity("全国");
        openPages.addAll(corgiOpenPageService.listOpenPage(corgiOpenPage));


        result = new ArrayList<>();
        if (!CollectionUtils.isEmpty(openPages)) {
            result.add(openPages.get(new Random().nextInt(openPages.size())));
        }
        return new JsonResult(result);
    }

}

