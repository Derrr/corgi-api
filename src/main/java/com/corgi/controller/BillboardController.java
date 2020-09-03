package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiBillboardService;
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
@RequestMapping("billboard")
public class BillboardController extends BaseController {
    @Reference
    private CorgiBillboardService corgiBillboardService;

    @GetMapping("get_by_date")
    public JsonResult getByDate(@RequestParam("startDate") String startDate, @RequestParam("endDate") String endDate) {
        return new JsonResult(corgiBillboardService.getBillboardByDate(startDate, endDate));
    }

    @GetMapping("update_by_nickname")
    public JsonResult updateByNickname(@RequestParam("from") String from, @RequestParam("to") String to, @RequestParam("date") String date) {
        corgiBillboardService.updateBillboardByNickname(from, to, date);
        return new JsonResult();
    }
}
