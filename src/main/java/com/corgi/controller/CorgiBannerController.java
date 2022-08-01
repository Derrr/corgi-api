package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiBannerService;
import com.corgi.user.entity.CorgiBanner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("banner")
public class CorgiBannerController extends BaseController {

    @Reference
    private CorgiBannerService corgiBannerService;

    @PostMapping("add_banner")
    public JsonResult addBanner(@RequestBody CorgiBanner corgiBanner) {
        if (hasUserId()) {
            return new JsonResult();
        }
        if (StringUtils.isEmpty(corgiBanner.getTopic())) {
            corgiBanner.setTopic("-");
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
        corgiBanner.setUrlType("12");
        return new JsonResult(Arrays.asList(corgiBanner));
//        List<CorgiBanner> bannerList = corgiBannerService.listBanner(corgiBanner);
//        return new JsonResult(bannerList);
    }

//    private boolean containBanner(List<CorgiBanner> bannerList, CorgiBanner singleBanner) {
//        for (CorgiBanner banner : bannerList) {
//            if (banner.getId().equals(singleBanner.getId())) {
//                return true;
//            }
//        }
//        return false;
//    }

}

