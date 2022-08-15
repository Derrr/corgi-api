package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.entity.CorgiBarReservation;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import com.corgi.user.enums.MerchandiseEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
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
    @Reference
    private CorgiOrderService corgiOrderService;
    @Reference
    private CorgiBarService corgiBarService;

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

    @GetMapping("get_reservation_by_order")
    public JsonResult getReservationByOrder(@RequestParam("orderNo") String orderNo) {
        CorgiOrder order = corgiOrderService.getOrderByTradeNo(orderNo);
        if (order != null && !StringUtils.isEmpty(order.getMarketId())) {
            CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(order.getMerchId(), getUserId());
            if (merchandise != null && CorgiMerchandise.RESERVE.equals(merchandise.getType())) {
                BarReservation reservation = corgiReserveService.getReservationById(order.getMarketId());
                if (reservation != null) {
                    CorgiBarReservation corgiBarReservation = new CorgiBarReservation();
                    BeanUtils.copyProperties(reservation,corgiBarReservation);
                    corgiBarReservation.setPayAmount(order.getPayAmount());
                    corgiBarReservation.setBarProfile(corgiBarService.getBarProfile(reservation.getBarId()));
                }
            }
        }
        return new JsonResult();
    }

}
