package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.exception.PermissionException;
import com.corgi.service.CorgiPayService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("order")
public class CorgiOrderController extends BaseController {
    @Reference
    private CorgiOrderService corgiOrderService;
    @Reference
    private CorgiPayService corgiPayService;

    @Autowired
    private MQService mqService;


    @GetMapping("pay")
    public JsonResult pay(@RequestParam("merchId") String merchId, @RequestParam("payType") String payType) {
        CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(merchId);
        if (merchandise == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "商品不存在");
        }
        if (merchandise.getStatus() == null || "0".equals(merchandise.getStatus())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "商品已过期");
        }
        CorgiOrder order = CorgiOrder.builder()
                .userId(getUserId())
                .payType(payType)
                .marketId("-")
                .sellerId("corgi")
                .build();
        if (CorgiOrder.PAY_TYPE.ALIPAY.equals(payType)) {
            return new JsonResult(corgiPayService.getAlipayOrder(merchandise, order));
        }
        if (CorgiOrder.PAY_TYPE.WX.equals(payType)) {
            return new JsonResult(corgiPayService.getWXPayOrder(merchandise, order));
        }
        return new JsonResult();
    }

    @GetMapping("get_merchandises")
    public JsonResult getMerchandise(@RequestParam("type") String type) {
        CorgiMerchandise query = new CorgiMerchandise();
        query.setType(type);
        return new JsonResult(corgiOrderService.getMerchandise(query));
    }

    @PostMapping("wx_callback")
    public JsonResult wxCallback(HttpServletRequest request) {
        log.info("callback:{} ", this.convertRequestParamsToMap(request));
        return new JsonResult();
    }


    @PostMapping("alipay_callback")
    public JsonResult alipayCallback(HttpServletRequest request) {
        log.info("callback:{} ", this.convertRequestParamsToMap(request));
        return new JsonResult();
    }

    // 将request中的参数转换成Map
    private Map<String, String> convertRequestParamsToMap(HttpServletRequest request) {
        Map<String, String> retMap = new HashMap();

        Set<Map.Entry<String, String[]>> entrySet = request.getParameterMap().entrySet();

        for (Map.Entry<String, String[]> entry : entrySet) {
            String name = entry.getKey();
            String[] values = entry.getValue();
            int valLen = values.length;

            if (valLen == 1) {
                retMap.put(name, values[0]);
            } else if (valLen > 1) {
                StringBuilder sb = new StringBuilder();
                for (String val : values) {
                    sb.append(",").append(val);
                }
                retMap.put(name, sb.substring(1));
            } else {
                retMap.put(name, "");
            }
        }

        return retMap;
    }

}

