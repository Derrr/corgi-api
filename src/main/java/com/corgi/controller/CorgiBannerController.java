package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiBannerService;
import com.corgi.user.entity.CorgiBanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("bar")
public class CorgiBannerController extends BaseController {

    @Reference
    private CorgiBannerService corgiBannerService;

    @PostMapping("add_banner")
    public JsonResult addBanner(@RequestBody CorgiBanner corgiBanner) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiBannerService.addBanner(corgiBanner);
        return new JsonResult();
    }

    @PostMapping("update_banner")
    public JsonResult updateBanner(@RequestBody CorgiBanner corgiBanner) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiBannerService.updateBanner(corgiBanner);
        return new JsonResult();
    }

    @GetMapping("delete_banner")
    public JsonResult deleteBanner(@RequestParam("bannerId") Integer bannerId) {
        if (hasUserId()) {
            return new JsonResult();
        }
        corgiBannerService.deleteBanner(bannerId);
        return new JsonResult();
    }

    @GetMapping("list_banner")
    public JsonResult listBanner(CorgiBanner corgiBanner) {
        return new JsonResult(corgiBannerService.listBanner(corgiBanner));
    }

    @GetMapping("get_banner")
    public JsonResult getBanner(CorgiBanner corgiBanner) {
        corgiBanner.setStatus(CorgiBanner.STATUS_ENABLE);
        return new JsonResult(corgiBannerService.listBanner(corgiBanner));
    }

}

