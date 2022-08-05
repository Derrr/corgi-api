package com.corgi.controller;

import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.IOUtils;
import org.apache.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.internal.util.AlipaySignature;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.constant.PayConstans;
import com.corgi.common.util.JWTUtils;
import com.corgi.common.util.UuidUtil;
import com.corgi.common.wxpay.sdk.*;
import com.corgi.entity.CorgiUserOrder;
import com.corgi.exception.PermissionException;
import com.corgi.service.CorgiPayService;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import com.corgi.user.enums.MerchandiseEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private CorgiActivityService corgiActivityService;
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private CorgiPayService corgiPayService;
    @Autowired
    private CorgiUtilService corgiUtilService;
    @Autowired
    private WXPay wxPay;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("update")
    public JsonResult update(@RequestBody CorgiOrder order) {
        if (hasUserId()) {
            return new JsonResult();
        }
        if (CorgiOrder.STATUS.SUCCESS.equals(order.getStatus())) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            order.setPayTime(sdf.format(new Date()));
        }
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }

    @PostMapping("withdraw")
    public JsonResult withdraw(@RequestBody CorgiOrder order) {
        String key = "withdraw_" + getUserId();
        if (order.getPayAmount() == null || order.getPayAmount() < 50) {
            return new JsonResult(Constants.API_ERROR_CODE, "提现金额不可低于50");
        }
        UserDetail detail = corgiUserService.getUserDetailBasic(getUserId());
        CorgiOrder query = CorgiOrder.builder()
                .status(CorgiOrder.STATUS.SUCCESS)
                .sellerId(getUserId())
                .build();
        Double totalIncome = corgiOrderService.countIncome(query);
        query.setSellerId(null);
        query.setUserId(getUserId());
        query.setPayType(CorgiOrder.PAY_TYPE.WITHDRAW);
        Double successWithdraw = corgiOrderService.countIncome(query);
        query.setStatus(CorgiOrder.STATUS.CREATED);
        Double withdrawing = corgiOrderService.countIncome(query);
        Double totalWithdraw = withdrawing + successWithdraw;
        Double rate = 0.6;
        if ("influencer".equals(detail.getAvatarStatus())) {
            rate = 0.65;
        }
        if (order.getPayAmount() > rate * totalIncome - totalWithdraw) {
            return new JsonResult(Constants.API_ERROR_CODE, "提现金额超出可提现余额");
        }
        corgiUtilService.lock(key);
        try {
            CorgiOrder orderQuery = CorgiOrder.builder()
                    .status(CorgiOrder.STATUS.CREATED)
                    .userId(getUserId())
                    .payType(CorgiOrder.PAY_TYPE.WITHDRAW)
                    .build();
            List<CorgiOrder> postOrders = corgiOrderService.getOrderByPage(orderQuery, 1, 10);
            if (CollectionUtils.isNotEmpty(postOrders)) {
                return new JsonResult(Constants.API_ERROR_CODE, "您有一笔提现尚未完成，完成后可继续提现");
            }
            orderQuery.setStatus(null);
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM");
            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
            String dateStr = sdf1.format(new Date());
            Date nowDate = sdf1.parse(dateStr);
            orderQuery.setCtime(sdf2.format(nowDate));
//            postOrders = corgiOrderService.getOrderByPage(orderQuery, 1, 10);
//            int i = 0;
//            for (CorgiOrder nowMonthOrder : postOrders) {
//                if (nowMonthOrder.getStatus().equals(CorgiOrder.STATUS.CLOSE) || nowMonthOrder.getStatus().equals(CorgiOrder.STATUS.SUCCESS)) {
//                    i++;
//                }
//                if (i > 4) {
//                    return new JsonResult(Constants.API_ERROR_CODE, "本月提现次数已满");
//                }
//            }
            order.setUserId(getUserId());
            order.setOrderId((Long.toHexString(System.currentTimeMillis() / 1000)).toUpperCase());
            order.setPayType(CorgiOrder.PAY_TYPE.WITHDRAW);
            order.setTradeNo(UuidUtil.getTradeNo(getUserId()));
            order.setSellerId("Corgi-app");
            order.setMarketId("-");
            order.setMerchId("-");
            order.setDesc("提现申请");
            corgiOrderService.addOrder(order);
            return new JsonResult(order);
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
            return new JsonResult(Constants.API_ERROR_CODE, "提现申请失败");
        } finally {
            corgiUtilService.unlock(key);
        }
    }

    @GetMapping("pay")
    public JsonResult pay(@RequestParam("merchId") String merchId,
                          @RequestParam(required = false, name = "goodsId") String goodsId,
                          @RequestParam("payType") String payType) {
        String key = "user_pay_" + getUserId();
        if (!redisTemplate.opsForValue().setIfAbsent(key.concat("attack"), "1", 2L, TimeUnit.SECONDS)) {
            return new JsonResult();
        }
        corgiUtilService.lock(key);
        try {
            CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(merchId, getUserId());
            if (StringUtils.isEmpty(goodsId) && CorgiMerchandise.ACTIVITY.equals(merchandise.getType())) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "参数错误");
            }
            if (!hasUserId()) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "参数错误");
            }
            if (merchandise == null) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "商品不存在");
            }
            if ("首购".equals(merchandise.getDisReason())) {
                CorgiUserGoods orderQuery = CorgiUserGoods.builder()
                        .userId(getUserId())
                        .goodsType(CorgiUserGoods.GOODS_TYPE.SUBSCRIBE)
                        .start(0)
                        .size(1)
                        .build();
                List<CorgiUserGoods> orders = corgiOrderService.getUserGoods(orderQuery);
                if (CollectionUtils.isNotEmpty(orders)) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "首购折扣商品不能重复购买");
                }
            }
            if (merchandise.getStatus() == null || "0".equals(merchandise.getStatus())) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "商品已失效");
            }

            CorgiOrder orderQuery = CorgiOrder.builder()
                    .userId(getUserId())
                    .status(CorgiOrder.STATUS.CREATED)
                    .merchType(merchandise.getType())
                    .build();
            List<CorgiOrder> postOrders = corgiOrderService.getOrderByPage(orderQuery, 1, 10);
            if (CollectionUtils.isNotEmpty(postOrders)) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "还有待付款的商品");
            }

            String marketId = "-";
            String sellerId = "corgi";
            if (StringUtils.isNotEmpty(goodsId)) {
                List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(goodsId));
                if (CollectionUtils.isEmpty(corgiActivities)) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "动态已不存在");
                }
                CorgiActivity activity = corgiActivities.get(0);
                if (!CorgiActivity.CAT_PAYING.equals(activity.getCategory())) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "该动态不是付费动态");
                }
                if (!merchId.equals(activity.getMerchId()) && !merchId.equals(activity.getAppMerchId())) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "动态付费状态存在异常");
                }
                CorgiUserGoods query = new CorgiUserGoods();
                query.setUserId(getUserId());
                query.setGoodsId(goodsId);
                query.setGoodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY);
                List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(query);
                if (CollectionUtils.isNotEmpty(goods)) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "该动态已付费");
                }
                marketId = activity.getMarketId();
                sellerId = activity.getUserId();
            }

            HashMap<String, Object> result = new HashMap<>();
            String tradeNo = UuidUtil.getTradeNo(getUserId());
            CorgiOrder order = CorgiOrder.builder()
                    .userId(getUserId())
                    .payType(payType)
                    .marketId(marketId)
                    .desc(MerchandiseEnum.getByCode(merchandise.getId()).getDesc())
                    .merchId(merchandise.getId())
                    .tradeNo(tradeNo)
                    .sellerId(sellerId)
                    .payAmount(merchandise.getPrice())
                    .build();
            result.put("orderString", "");
            if (CorgiOrder.PAY_TYPE.ALIPAY.equals(payType)) {
                result.put("orderString", corgiPayService.getAlipayOrder(merchandise, order));
            }
            if (CorgiOrder.PAY_TYPE.WX.equals(payType)) {
                result.put("orderString", corgiPayService.getWXPayOrder(merchandise, order));
            }
            if (CorgiOrder.PAY_TYPE.IN_APP.equals(payType)) {
                corgiOrderService.addOrder(order);
            }
            result.put("orderNo", order.getTradeNo());
            result.put("merchandise", merchandise);
            return new JsonResult(result);
        } finally {
            corgiUtilService.unlock(key);
        }
    }

    @GetMapping("get_merchandises")
    public JsonResult getMerchandise(@RequestParam("type") String type) {
        CorgiMerchandise query = new CorgiMerchandise();
        query.setType(type);
        List<CorgiMerchandise> merchandises = corgiOrderService.getMerchandise(query);
        if (type.equals(CorgiMerchandise.SUBSCRIBE)) {
            CorgiUserGoods orderQuery = CorgiUserGoods.builder()
                    .userId(getUserId())
                    .goodsType(CorgiUserGoods.GOODS_TYPE.SUBSCRIBE)
                    .start(0)
                    .size(1)
                    .build();
            List<CorgiUserGoods> orders = corgiOrderService.getUserGoods(orderQuery);
            if (CollectionUtils.isNotEmpty(orders)) {
                merchandises = merchandises.stream().filter(m -> !MerchandiseEnum.isFirst(m.getId())).collect(Collectors.toList());
            } else {
                merchandises = merchandises.stream().filter(m -> MerchandiseEnum.isFirst(m.getId())).collect(Collectors.toList());
            }
        }
        return new JsonResult(merchandises);
    }

    @GetMapping("count_order")
    public JsonResult countOrder(@RequestParam(name = "type", required = false) String type,
                                 @RequestParam(name = "status", required = false) String status,
                                 @RequestParam(name = "userId", required = false) String userId) {
        if (hasUserId()) {
            return new JsonResult();
        }
        CorgiOrder query = CorgiOrder.builder()
                .userId(userId)
                .payType(type)
                .status(status)
                .build();
        return new JsonResult(corgiOrderService.countOrder(query));
    }

    @GetMapping("list_order")
    public JsonResult getOrders(
            @RequestParam(required = false, name = "type") String type,
            @RequestParam(required = false, name = "userId") String userId,
            @RequestParam("page") Integer page,
            @RequestParam("pageSize") Integer pageSize) {
        if (hasUserId()) {
            userId = getUserId();
        }
        CorgiOrder query = CorgiOrder.builder()
                .userId(userId)
                .payType(type)
                .status(CorgiOrder.STATUS.SUCCESS)
                .build();
        if (CorgiOrder.PAY_TYPE.WITHDRAW.equals(type)) {
            query.setStatus(null);
        }
        return new JsonResult(this.buildOrder(corgiOrderService.getOrderByPage(query, page, pageSize), getUserId()));
    }

    @GetMapping("success_order")
    public JsonResult updateOrder(@RequestParam("tradeNo") String tradeNo) {
        CorgiOrder order = corgiOrderService.getOrderByTradeNo(tradeNo);
        if (order != null && CorgiOrder.STATUS.CREATED.equals(order.getStatus())) {
            if (CorgiOrder.PAY_TYPE.WX.equals(order.getPayType())) {
                corgiPayService.queryWXOrder(order);
            }
            if (CorgiOrder.PAY_TYPE.ALIPAY.equals(order.getPayType())) {
                corgiPayService.queryAlipayOrder(order);
            }
        }
        return new JsonResult();
    }

    @GetMapping("close_order")
    public JsonResult closeOrder(@RequestParam("tradeNo") String tradeNo) {
        CorgiOrder order = corgiOrderService.getOrderByTradeNo(tradeNo);
        if (order == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "订单不存在");
        }
        if (!order.getStatus().equals(CorgiOrder.STATUS.CREATED)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "订单无法关闭");
        }
        try {
            if (CorgiOrder.PAY_TYPE.WX.equals(order.getPayTime())) {
                corgiPayService.wxCloseOrder(order);
                corgiOrderService.updateOrder(order);
                return new JsonResult();
            }
            if (CorgiOrder.PAY_TYPE.ALIPAY.equals(order.getPayTime())) {
                corgiPayService.alipayCloseOrder(order);
                corgiOrderService.updateOrder(order);
                return new JsonResult();
            }
        } catch (Exception e) {
            order.setResult(e.getMessage());
            corgiOrderService.updateOrder(order);
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "订单关闭失败");
        }
        order.setResult("直接关闭订单");
        order.setStatus(CorgiOrder.STATUS.CLOSE);
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }

    @GetMapping("search_order")
    public JsonResult searchOrders(CorgiOrder order, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (hasUserId()) {
            order.setUserId(getUserId());
        }
        List<CorgiOrder> orders = corgiOrderService.getOrderByPage(order, page, pageSize);
        if (CollectionUtils.isNotEmpty(orders)) {
            for (CorgiOrder order1 : orders) {
                UserDetail detail = corgiUserService.getUserDetailBasic(order1.getUserId());
                if (detail != null) {
                    order1.setBuyerId(detail.getNickname());
                }
            }
        }
        return new JsonResult(orders);

    }

    @PostMapping("receipt_update")
    public JsonResult updateReceipt(@RequestBody HashMap<String, String> receipt) {
        String tradeNo = receipt.get("tradeNo");
        String receiptString = receipt.get("receipt");
        corgiOrderService.updateReceipt(tradeNo, receiptString);
        return new JsonResult();
    }

    @PostMapping("applepay_verify")
    public JsonResult applyPayVerify(@RequestBody HashMap<String, String> receipt) {
        String tradeNo = receipt.get("tradeNo");
        String receiptData = receipt.get("receipt");
        if(StringUtils.isEmpty(receiptData)){
            receiptData = receiptData.replace(" ","+");
        }
        String transactionId = receipt.get("transactionId");
        CorgiOrder order = corgiOrderService.getOrderByTradeNo(tradeNo);
        if (order == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "订单不存在");
        }
        String password = null;
        //CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(order.getMerchId(), getUserId());
        //if (CorgiMerchandise.SUBSCRIBE.equals(merchandise.getType())) {
        password = "e17ba249e26e49f3ac256eb0838903a4";
        //}
        JSONObject result = corgiPayService.verifyApplePay(receiptData, password);
        order.setResult(result.toJSONString());
        if ("0".equals(result.getString("status"))) {
            order.setStatus(CorgiOrder.STATUS.SUCCESS);
        } else {
            order.setStatus(CorgiOrder.STATUS.FAIL);
        }
        JSONObject receiptResult = result.getJSONObject("receipt");
        order.setResult(result.toJSONString());
        String key = "verify_apple-" + getUserId();
        try {
            corgiUtilService.lock(key);
            if (receiptResult != null) {
                order.setPayTime(result.getString("original_purchase_date_ms"));
                JSONArray inApps = receiptResult.getJSONArray("in_app");
                if (inApps != null) {
                    JSONObject inApp = null;
                    if (1 == inApps.size()) {
                        inApp = inApps.getJSONObject(0);
                    } else {
                        for (int i = 0; i < inApps.size(); i++) {
                            JSONObject orderItem = inApps.getJSONObject(i);
                            if (orderItem.getString("transaction_id").equals(transactionId)) {
                                inApp = orderItem;
                            }
                        }
                    }
                    if (null == inApp) {
                        inApps = result.getJSONArray("latest_receipt_info");
                        inApp = inApps.getJSONObject(0);
                    }
                    if (null == inApp) {
                        order.setStatus(CorgiOrder.STATUS.FAIL);
                        order.setOrderId(transactionId);
                        corgiOrderService.updateOrder(order);
                        corgiOrderService.updateReceipt(tradeNo, receiptData);
                        return new JsonResult(Constants.PARAMETER_ERROR_CODE, "验证结果中不存在订单信息 ");
                    } else {
                        order.setBuyerId(inApp.getString("expires_date_ms"));
                        order.setPayTime(inApp.getString("original_purchase_date_ms"));
                        order.setOrderId(inApp.getString("transaction_id"));
                    }
                }
            }
            corgiOrderService.updateOrder(order);
            corgiOrderService.updateReceipt(tradeNo, receiptData);
        } finally {
            corgiUtilService.unlock(key);
        }
        return new JsonResult();
    }

    @GetMapping("get_income_info")
    public JsonResult getIncomeInfo(@RequestParam("userId") String userId) {
        if (hasUserId()) {
            userId = getUserId();
        }
        UserDetail detail = corgiUserService.getUserDetailBasic(userId);
        HashMap result = new HashMap();
        CorgiOrder query = CorgiOrder.builder()
                .status(CorgiOrder.STATUS.SUCCESS)
                .sellerId(userId)
                .build();
        Double totalIncome = corgiOrderService.countIncome(query);
        query.setSellerId(null);
        query.setUserId(userId);
        query.setPayType(CorgiOrder.PAY_TYPE.WITHDRAW);
        Double successWithdraw = corgiOrderService.countIncome(query);
        query.setStatus(CorgiOrder.STATUS.CREATED);
        Double withdrawing = corgiOrderService.countIncome(query);
        Double totalWithdraw = successWithdraw + withdrawing;
        CorgiOrder withdrawQuery = new CorgiOrder();
        withdrawQuery.setStatus(CorgiOrder.STATUS.CREATED);
        withdrawQuery.setPayType(CorgiOrder.PAY_TYPE.WITHDRAW);
        withdrawQuery.setUserId(userId);
        List<CorgiOrder> orders = corgiOrderService.getOrderByPage(withdrawQuery, 1, 1);
        if (CollectionUtils.isNotEmpty(orders)) {
            result.put("withdrawOrder", orders);
        }
        Double rate = 0.6;
        if ("influencer".equals(detail.getAvatarStatus())) {
            rate = 0.65;
        }
        result.put("totalIncome", totalIncome);
        result.put("totalWithdraw", totalWithdraw);
        result.put("remainWithdraw", totalIncome * rate - totalWithdraw);
        return new JsonResult(result);
    }

    @PostMapping("wx_callback")
    public JsonResult wxCallback(HttpServletRequest request) {
        CorgiOrder order = CorgiOrder.builder().build();
        Map<String, String> params = new HashMap<>();
        String bodyStr = "";
        try {
            BufferedReader bufferedReader = request.getReader();
            bodyStr = IOUtils.read(bufferedReader);
            params = wxPay.processResponseXml(bodyStr);
            order = buildWXOrder(params);
        } catch (Exception e) {
            params.put("getError", e.getMessage());
            params.put("bodyStr", bodyStr);
            log.error(e.getMessage(), e);
        }

        try {
            if (!WXPayUtil.isSignatureValid(params, CorgiWXPayConfig.config.getKey())) {
                order.setStatus(CorgiOrder.STATUS.CREATED);
                throw new PermissionException("微信回调签名认证失败");
            } else if (WXPayConstants.FAIL.equals(params.get("return_code"))) {
                order.setStatus(CorgiOrder.STATUS.CREATED);
            } else if (WXPayConstants.FAIL.equals(params.get("result_code"))) {
                order.setStatus(CorgiOrder.STATUS.FAIL);
            } else {
                order.setStatus(CorgiOrder.STATUS.SUCCESS);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            params.put("checkError", e.getMessage());
            order.setStatus(CorgiOrder.STATUS.FAIL);
        }
        order.setResult(JSON.toJSONString(params));
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }

    @Deprecated
    @PostMapping("applepay_callback")
    public JsonResult applepayCallback(@RequestBody String signedPayload) {
        log.info("callback:{} ", signedPayload);
        try {
            DecodedJWT decodedJWT = JWTUtils.verifyToken(signedPayload);
            String notificationType = decodedJWT.getClaim("notificationType").asString();
            if ("REFUND".equals(notificationType)) {
                HashMap data = decodedJWT.getClaim("data").as(HashMap.class);
                DecodedJWT obj = JWTUtils.verifyToken(signedPayload);
                CorgiOrder order = CorgiOrder.builder()
                        .orderId(obj.getClaim("transactionId").asString())
                        .result(JSON.toJSONString(decodedJWT))
                        .build();
                corgiOrderService.subscribe(order, null, "0", "");
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new JsonResult();
    }

    @PostMapping("applepay_subscribe")
    public JsonResult applepaySubscribe(@RequestBody HashMap<String, String> request) {
        String receipt = request.get("receipt");
        String vipStatus = request.get("vipStatus");
        String vipDate = request.get("vipDate");
        String payAmount = request.get("payAmount");
        String transactionId = request.get("transactionId");
        String buyerId = request.get("buyerId");
        String tradeNo = corgiOrderService.getReceipt(getUserId(), receipt);
        if (StringUtils.isNotEmpty(tradeNo)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "票据已存在 ");
        }
        tradeNo = UuidUtil.getTradeNo(getUserId());
        CorgiOrder order = CorgiOrder.builder()
                .userId(getUserId())
                .payAmount(Double.parseDouble(payAmount))
                .payType(CorgiOrder.PAY_TYPE.IN_APP)
                .tradeNo(tradeNo)
                .marketId("-")
                .sellerId("corgi")
                .status(CorgiOrder.STATUS.SUCCESS)
                .build();
        JSONObject result = corgiPayService.verifyApplePay(receipt, "e17ba249e26e49f3ac256eb0838903a4");
        order.setResult(result.toJSONString());
        if ("0".equals(result.getString("status"))) {
            order.setStatus(CorgiOrder.STATUS.SUCCESS);
        } else {
            order.setStatus(CorgiOrder.STATUS.FAIL);
        }
        JSONObject receiptResult = result.getJSONObject("receipt");
        if (receiptResult != null) {
            order.setPayTime(result.getString("original_purchase_date_ms"));
            JSONArray inApps = receiptResult.getJSONArray("in_app");
            if (inApps != null) {
                JSONObject inApp = null;
                if (1 == inApps.size()) {
                    inApp = inApps.getJSONObject(0);
                } else {
                    for (int i = 0; i < inApps.size(); i++) {
                        JSONObject orderItem = inApps.getJSONObject(i);
                        if (orderItem.getString("transaction_id").equals(transactionId)) {
                            inApp = orderItem;
                        }
                    }
                }
                if (null == inApp) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "验证结果中不存在订单信息 ");
                } else {
                    if (StringUtils.isNotEmpty(buyerId)) {
                        order.setBuyerId(buyerId);
                    }
                    order.setPayTime(inApp.getString("original_purchase_date_ms"));
                    order.setOrderId(inApp.getString("transaction_id"));
                }
            }
        }
        CorgiUserGoods goods = CorgiUserGoods.builder()
                .userId(order.getUserId())
                .goodsType(CorgiUserGoods.GOODS_TYPE.SUBSCRIBE)
                .currency(CorgiUserGoods.CURRENCY.CNY)
                .traderId("corgi")
                .price(order.getPayAmount())
                .tradeNo(order.getTradeNo())
                .build();
        corgiOrderService.subscribe(order, goods, vipStatus, vipDate);
        corgiOrderService.updateReceipt(tradeNo, receipt);
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
            order.setResult(e.getMessage());
            order.setStatus(PayConstans.FAIL);
        }
        order.setResult(JSON.toJSONString(params));
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }

    private CorgiOrder buildWXOrder(Map<String, String> params) {
        CorgiOrder order = CorgiOrder.builder()
                .tradeNo(params.get("out_trade_no"))
                .buyerId(params.get("openid"))
                .payTime(params.get("time_end"))
                .orderId(params.get("transaction_id"))
                .build();
        try {
            order.setPayAmount(Integer.valueOf(params.get("total_fee")) / 100.0);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return order;
    }

    private CorgiOrder buildAlipayOrder(Map<String, String> params) {
        CorgiOrder order = CorgiOrder.builder()
                .tradeNo(params.get("out_trade_no"))
                .buyerId(params.get("buyer_logon_id"))
                .payTime(params.get("gmt_payment"))
                .orderId(params.get("trade_no"))
                .build();
        try {
            order.setPayAmount(Double.valueOf(params.get("total_amount")));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return order;
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

    List<CorgiUserOrder> buildOrder(List<CorgiOrder> orders, String userId) {
        List<CorgiUserOrder> result = new ArrayList<>();
        HashMap<String, CorgiMerchandise> merchandiseHashMap = new HashMap<>();
        for (CorgiOrder order : orders) {
            CorgiUserOrder corgiUserOrder = new CorgiUserOrder();
            BeanUtils.copyProperties(order, corgiUserOrder);
            if (merchandiseHashMap.get(order.getMerchId()) == null) {
                CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(order.getMerchId(), userId);
                merchandiseHashMap.put(order.getMerchId(), merchandise);
            }
            CorgiUserGoods query = new CorgiUserGoods();
            query.setGoodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY);
            query.setTradeNo(order.getTradeNo());
            query.setStart(0);
            query.setSize(1);
            List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(query);
            corgiUserOrder.setMerchandise(merchandiseHashMap.get(order.getMerchId()));
            if (CollectionUtils.isNotEmpty(goods)) {
                List<CorgiActivity> activities = corgiActivityService.getActivityByIds(Arrays.asList(goods.get(0).getId()));
                if (CollectionUtils.isNotEmpty(activities)) {
                    corgiUserOrder.setActivity(activities.get(0));
                }
            }
            result.add(corgiUserOrder);
        }
        return result;
    }


}

