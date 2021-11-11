package com.corgi.controller;

import com.alibaba.dubbo.common.utils.IOUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alipay.api.internal.util.AlipaySignature;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.constant.PayConstans;
import com.corgi.common.wxpay.sdk.CorgiWXPayConfig;
import com.corgi.common.wxpay.sdk.WXPayConfig;
import com.corgi.common.wxpay.sdk.WXPayUtil;
import com.corgi.service.CorgiPayService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
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


    @GetMapping("pay")
    public JsonResult pay(@RequestParam("merchId") String merchId, @RequestParam("payType") String payType) {
        CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(merchId);
        if (merchandise == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "商品不存在");
        }
        if (merchandise.getStatus() == null || "0".equals(merchandise.getStatus())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "商品已过期");
        }
        HashMap<String, Object> result = new HashMap<>();
        CorgiOrder order = CorgiOrder.builder()
                .userId(getUserId())
                .payType(payType)
                .marketId("-")
                .sellerId("corgi")
                .build();
        result.put("orderString", "");
        if (CorgiOrder.PAY_TYPE.ALIPAY.equals(payType)) {
            result.put("orderString", corgiPayService.getAlipayOrder(merchandise, order));
        }
        if (CorgiOrder.PAY_TYPE.WX.equals(payType)) {
            result.put("orderString", corgiPayService.getWXPayOrder(merchandise, order));
        }
        result.put("orderNo", order.getTradeNo());
        return new JsonResult(result);
    }

    @GetMapping("get_merchandises")
    public JsonResult getMerchandise(@RequestParam("type") String type) {
        CorgiMerchandise query = new CorgiMerchandise();
        query.setType(type);
        return new JsonResult(corgiOrderService.getMerchandise(query));
    }

    @GetMapping("list_order")
    public JsonResult getOrders(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        CorgiOrder order = CorgiOrder.builder()
                .userId(getUserId())
                .build();
        return new JsonResult(corgiOrderService.getOrderByPage(order, page, pageSize));
    }

    @GetMapping("search_order")
    public JsonResult searchOrders(@RequestParam("order") CorgiOrder order, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (hasUserId()) {
            order.setUserId(getUserId());
        }
        return new JsonResult(corgiOrderService.getOrderByPage(order, page, pageSize));
    }

    @PostMapping("wx_callback")
    public JsonResult wxCallback(HttpServletRequest request) {

        String str = request.getQueryString();
        String bodyStr = null;
        try {
            BufferedReader bufferedReader = request.getReader();
            bodyStr = IOUtils.read(bufferedReader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("bodyStr:{} queryStr:{} ", bodyStr, str);

        Map<String, String> params = this.convertRequestParamsToMap(request);
        log.info("callback:{} ", params);
        CorgiOrder order = buildWXOrder(params);
        try {
            if (!WXPayUtil.isSignatureValid(params, CorgiWXPayConfig.config.getKey())) {
                log.info("微信回调签名认证失败，signVerified=false, paramsJson:{}", params);
                order.setStatus(PayConstans.FAIL);
            } else if (PayConstans.WX.FAIL.equals(params.get("return_code"))) {
                order.setStatus(PayConstans.FAIL);
            } else if (PayConstans.WX.FAIL.equals(params.get("result_code"))) {
                order.setStatus(PayConstans.FAIL);
            } else {
                order.setStatus(PayConstans.SUCCESS);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            order.setStatus(PayConstans.FAIL);
        }
        order.setResult(JSON.toJSONString(params));
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }


    @PostMapping("alipay_callback")
    public JsonResult alipayCallback(HttpServletRequest request) {
        Map<String, String> params = this.convertRequestParamsToMap(request);
        log.info("callback:{} ", params);
        CorgiOrder order = buildAlipayOrder(params);
        try {
            // 调用SDK验证签名
            boolean signVerified = AlipaySignature.rsaCheckV1(params, CorgiPayService.ALIPAY_PUBLIC_KEY,
                    params.get("charset"), params.get("sign_type"));
            if (signVerified) {
                log.info("支付宝回调签名认证成功");
                // 另起线程处理业务
                String trade_status = params.get("trade_status");
                // 支付成功
                if (trade_status.equals(PayConstans.ALIPAY.TRADE_SUCCESS)
                        || trade_status.equals(PayConstans.ALIPAY.TRADE_FINISHED)) {
                    // TODO 处理支付成功逻辑
                    order.setStatus(PayConstans.SUCCESS);
                    corgiOrderService.updateOrder(order);
                } else {
                    order.setStatus(PayConstans.CLOSE);
                }
            } else {
                order.setStatus(PayConstans.FAIL);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            order.setStatus(PayConstans.FAIL);
        }
        order.setResult(JSON.toJSONString(params));
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }

    private CorgiOrder buildWXOrder(Map<String, String> params) {
        return CorgiOrder.builder()
                .tradeNo(params.get("out_trade_no"))
                .buyerId(params.get("buyer_logon_id"))
                .payTime(params.get("gmt_payment"))
                .build();
    }

    private CorgiOrder buildAlipayOrder(Map<String, String> params) {
        return CorgiOrder.builder()
                .tradeNo(params.get("out_trade_no"))
                .buyerId(params.get("buyer_logon_id"))
                .payTime(params.get("gmt_payment"))
                .build();
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

