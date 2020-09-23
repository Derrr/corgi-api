package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiBannerService;
import com.corgi.user.api.CorgiOpenPageService;
import com.corgi.user.entity.CorgiBanner;
import com.corgi.user.entity.CorgiOpenPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("open_page")
public class CorgiOpenPageController extends BaseController {

    @Reference
    private CorgiOpenPageService corgiOpenPageService;

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
        corgiOpenPage.setStatus(CorgiOpenPage.STATUS_ENABLE);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        corgiOpenPage.setStartTime(sdf.format(new Date()));
        List<CorgiOpenPage> openPages = corgiOpenPageService.listOpenPage(corgiOpenPage);
        if(CollectionUtils.isEmpty(openPages)){
            corgiOpenPage.setCity("全国");
            openPages = corgiOpenPageService.listOpenPage(corgiOpenPage);
        }
        return new JsonResult(openPages);
    }

}

