package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("reserve")
public class ReserveController extends BaseController {
    @Reference
    private CorgiReserveService corgiReserveService;

    @PostMapping("add")
    public JsonResult add(@RequestBody BarReservation reservation) {
        reservation.setUserId(getUserId());
        return new JsonResult(corgiReserveService.addReservation(reservation));
    }

    @GetMapping("list_reservations")
    public JsonResult listReservation(BarReservation reservation, @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        return new JsonResult(corgiReserveService.listAllReservation(reservation, (page - 1) * size, size));
    }

    @GetMapping("count_reservation")
    public JsonResult countReservation(BarReservation reservation) {
        return new JsonResult(corgiReserveService.countReservation(reservation));
    }

    @PostMapping("update_reservation")
    public JsonResult updateReservation(@RequestBody BarReservation barReservation) {
       corgiReserveService.updateReservation(barReservation);
        return new JsonResult();
    }

}
