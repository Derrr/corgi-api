package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alipay.api.internal.util.AlipaySignature;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.service.CorgiPayService;
import com.corgi.service.MQService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("order")
public class CorgiOrderController extends BaseController {
    @Reference
    private CorgiOrderService corgiOrderService;
    @Autowired
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
        Map<String, String> params = this.convertRequestParamsToMap(request);
        log.info("callback:{} ", params);

        try {
            // 调用SDK验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(params, CorgiPayService.ALIPAY_PUBLIC_KEY,
                    params.get("charset"), params.get("sign_type"));
            if (signVerified) {
                log.info("支付宝回调签名认证成功");
                // 另起线程处理业务
                String trade_status = params.get("trade_status");
                // 支付成功
                if (trade_status.equals("TRADE_SUCCESS")
                        || trade_status.equals("TRADE_FINISHED")) {
                    // TODO 处理支付成功逻辑
                    try {

                    } catch (Exception e) {
                        log.error("支付宝回调业务处理报错,params:" + params, e);
                    }
                } else {
                    log.error("没有处理支付宝回调业务，支付宝交易状态：{},params:{}", trade_status, params);
                }
            } else {
                log.info("支付宝回调签名认证失败，signVerified=false, paramsJson:{}", params);
            }
        } catch (Exception e) {
            log.error("支付宝回调签名认证失败,paramsJson:{},errorMsg:{}", params, e.getMessage());
        }
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

