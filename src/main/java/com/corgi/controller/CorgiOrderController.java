package com.corgi.controller;

import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.alibaba.dubbo.common.utils.IOUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.internal.util.AlipaySignature;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.constant.PayConstans;
import com.corgi.common.util.RequestUtil;
import com.corgi.common.util.UuidUtil;
import com.corgi.common.wxpay.sdk.*;
import com.corgi.entity.CorgiUserOrder;
import com.corgi.exception.PermissionException;
import com.corgi.service.AliyunGreenService;
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
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.ECPublicKey;
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
    @Reference
    private CorgiBillboardService corgiBillboardService;
    @Reference
    private CorgiReserveService corgiReserveService;
    @Reference
    private CorgiUserWechatService corgiUserWechatService;
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
        order.setPackageName(RequestUtil.getPackageName());
        corgiOrderService.updateOrder(order);
        return new JsonResult();
    }

//    @GetMapping("refund")
//    public JsonResult refund(@RequestParam("tradeNo") String tradeNo) {
//        if (hasUserId()) {
//            return new JsonResult();
//        }
//        CorgiOrder order = corgiOrderService.getOrderByTradeNo(tradeNo);
//        try {
//            if (!CorgiOrder.STATUS.SUCCESS.equals(order.getStatus())) {
//                return new JsonResult(Constants.API_ERROR_CODE, "无法退单");
//            }
//            if (CorgiOrder.PAY_TYPE.WX.equals(order.getPayType())) {
//                corgiPayService.wxRefundOrder(order);
//            }
//
//            if (CorgiOrder.PAY_TYPE.ALIPAY.equals(order.getPayType())) {
//
//            }
//        } catch (Exception e) {
//            order.setResult(e.getMessage());
//        }
//        return new JsonResult();
//    }

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
            postOrders = corgiOrderService.getOrderByPage(orderQuery, 1, 10);
            int i = 0;
            for (CorgiOrder nowMonthOrder : postOrders) {
                if (nowMonthOrder.getStatus().equals(CorgiOrder.STATUS.CLOSE) || nowMonthOrder.getStatus().equals(CorgiOrder.STATUS.SUCCESS)) {
                    i++;
                }
                if (i > 4) {
                    return new JsonResult(Constants.API_ERROR_CODE, "本月提现次数已满");
                }
            }
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

    @GetMapping("pay_billboard")
    public JsonResult payBillboard(@RequestParam("merchId") String merchId,
                                   @RequestParam(name = "goodsId") String goodsId,
                                   @RequestParam(name = "date") String date,
                                   @RequestParam("payType") String payType) {
        String key = "billboard_pay_" + date;
        if (!redisTemplate.opsForValue().setIfAbsent(key.concat(getUserId()), "1", 2L, TimeUnit.SECONDS)) {
            return new JsonResult();
        }
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(goodsId));
        if (CollectionUtils.isEmpty(corgiActivities)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "动态已不存在");
        }
        CorgiActivity activity = corgiActivities.get(0);
        if (AliyunGreenService.CHECK_LIST.contains(activity.getCheckStatus())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "动态存在违规，不能上榜");
        }
        if (!CorgiActivity.CAT_IMAGE.contains(activity.getCategory())) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "该动态类型不能上榜");
        }
        corgiUtilService.lock(key);
        try {
            PaidBillboard paidBillboard = new PaidBillboard();
            paidBillboard.setDate(date);
            List<PaidBillboard> billboards = corgiBillboardService.queryPaidBillboard(paidBillboard, 1, 100);
            if (CollectionUtils.isNotEmpty(billboards)) {
                for (PaidBillboard billboard : billboards) {
                    if (PaidBillboard.CREATED.equals(billboard.getStatus())) {
                        continue;
                    }
                    if (goodsId.equals(billboard.getActivityId())) {
                        return new JsonResult(Constants.PARAMETER_ERROR_CODE, "动态在该日期已尝试上榜");
                    }
                    if (!PaidBillboard.FAIL.equals(billboard.getStatus())) {
                        return new JsonResult(Constants.PARAMETER_ERROR_CODE, "该日期已存在上榜动态");
                    }
                }
            }
            if (MerchandiseEnum.BILLBOARD_YEAR.getCode().equals(merchId) && !this.checkYearBillboard()) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "不满足免费上榜规则");
            }
            paidBillboard.setUserId(activity.getUserId());
            paidBillboard.setActivityId(goodsId);
            paidBillboard = corgiBillboardService.createPaidBillboard(paidBillboard);
            if (MerchandiseEnum.BILLBOARD_YEAR.getCode().equals(merchId)) {
                paidBillboard.setStatus(PaidBillboard.FREE);
                corgiBillboardService.updatePaiBillboard(paidBillboard);
                return new JsonResult();
            } else {
                CorgiMerchandise merchandise = corgiOrderService.getMerchandiseById(merchId, getUserId());
                HashMap<String, Object> result = this.payResult(payType, paidBillboard.getId(), "corgi", merchandise);
                paidBillboard.setTradeNo(result.get("orderNo") + "");
                corgiBillboardService.updatePaiBillboard(paidBillboard);
                return new JsonResult(result);
            }
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
                    .merchType(merchandise.getType().split("-")[0])
                    .build();
            List<CorgiOrder> postOrders = corgiOrderService.getOrderByPage(orderQuery, 1, 10);
            if (CollectionUtils.isNotEmpty(postOrders)) {
                Long minute = getCancelMinutes(postOrders);
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "还有待付款的商品，请 " + minute + "分钟 后再尝试购买");
            }

            String marketId = StringUtils.isEmpty(goodsId) ? "-" : goodsId;
            String sellerId = "corgi";
            if (merchandise.getType().equals(CorgiMerchandise.ACTIVITY)) {
                JsonResult result = new JsonResult();
                result.setCode(Constants.PARAMETER_ERROR_CODE);
                CorgiActivity activity = this.checkActivityPay(goodsId, merchId, result);
                if (activity == null) {
                    return result;
                }
                marketId = activity.getMarketId();
                sellerId = activity.getUserId();
            } else if (merchandise.getType().equals(CorgiMerchandise.LOCATION)) {
                JsonResult result = new JsonResult();
                result.setCode(Constants.PARAMETER_ERROR_CODE);
                if (!checkLocation(goodsId, result)) {
                    return result;
                }
            } else if (merchandise.getType().equals(CorgiMerchandise.LOCATIONMONTH)) {
                JsonResult result = new JsonResult();
                result.setCode(Constants.PARAMETER_ERROR_CODE);
                if (!checkLocationMonth(result)) {
                    return result;
                }
            } else if (merchandise.getType().equals(CorgiMerchandise.RESERVE)) {
                JsonResult result = new JsonResult();
                result.setCode(Constants.PARAMETER_ERROR_CODE);
                if (!checkReservePay(goodsId, result)) {
                    return result;
                }
            } else if (merchandise.getType().equals(CorgiMerchandise.WECHAT)) {
                JsonResult result = new JsonResult();
                result.setCode(Constants.PARAMETER_ERROR_CODE);
                sellerId = goodsId;
                UserWechat userWechat = checkWechatPay(goodsId, result);
                if (userWechat == null) {
                    return result;
                }
                marketId = userWechat.getId();
            }

            HashMap<String, Object> result = this.payResult(payType, marketId, sellerId, merchandise);
            if (merchandise.getType().equals(CorgiMerchandise.RESERVE)) {
                BarReservation update = new BarReservation();
                update.setId(goodsId);
                update.setTradeNo(result.get("orderNo") + "");
                update.setMerchId(merchId);
                corgiReserveService.updateReservation(update);
            }

            return new JsonResult(result);
        } finally {
            corgiUtilService.unlock(key);
        }
    }

    private boolean checkLocation(String goodsId, JsonResult result) {
        UserPosition position = corgiUserService.getUserPosition(goodsId);
        if (position == null || position.getLat() == null || position.getLng() == null
                || position.getLat() > 200 || position.getLng() > 200 || position.getLat() == 0 || position.getLng() == 0) {
            result.setMessage("定位失败");
            return false;
        }
        return true;
    }

    private boolean checkLocationMonth(JsonResult result) {
        String vipResult = corgiOrderService.getUserLocationExpireDate(getUserId());
        if (StringUtils.isNotEmpty(vipResult)) {
            result.setMessage("不能重复购买");
            return false;
        }
        return true;
    }

    private boolean checkReservePay(String goodsId, JsonResult result) {
        BarReservation query = new BarReservation();
        query.setId(goodsId);
        if (corgiReserveService.countReservation(query) == 0) {
            result.setMessage("订座不存在");
            return false;
        }
        CorgiUserGoods goodsQuery = new CorgiUserGoods();
        goodsQuery.setUserId(getUserId());
        goodsQuery.setGoodsId(goodsId);
        goodsQuery.setGoodsType(CorgiUserGoods.GOODS_TYPE.RESERVE);
        List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(goodsQuery);
        if (CollectionUtils.isNotEmpty(goods)) {
            result.setMessage("该订座已付费");
            return false;
        }
        return true;
    }

    private UserWechat checkWechatPay(String goodsId, JsonResult result) {
        UserWechat userWechat = corgiUserWechatService.getUserWechat(goodsId);
        if(userWechat == null){
            result.setMessage("该用户未开放微信购买");
            return null;
        }
        CorgiUserGoods goodsQuery = new CorgiUserGoods();
        goodsQuery.setUserId(getUserId());
        goodsQuery.setGoodsId(goodsId);
        goodsQuery.setGoodsType(CorgiMerchandise.WECHAT);
        List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(goodsQuery);
        if (CollectionUtils.isNotEmpty(goods)) {
            result.setMessage("该微信已购买");
            return null;
        }
        return userWechat;
    }

    private CorgiActivity checkActivityPay(String goodsId, String merchId, JsonResult result) {
        List<CorgiActivity> corgiActivities = corgiActivityService.getActivityByIds(Arrays.asList(goodsId));
        if (CollectionUtils.isEmpty(corgiActivities)) {
            result.setMessage("动态已不存在");
            return null;
        }
        CorgiActivity activity = corgiActivities.get(0);
        if (!CorgiActivity.CAT_PAYING.equals(activity.getCategory())) {
            result.setMessage("该动态不是付费动态");
            return null;
        }
        if (!merchId.equals(activity.getMerchId()) && !merchId.equals(activity.getAppMerchId())) {
            result.setMessage("动态付费状态存在异常");
            return null;
        }
        CorgiUserGoods query = new CorgiUserGoods();
        query.setUserId(getUserId());
        query.setGoodsId(goodsId);
        query.setGoodsType(CorgiUserGoods.GOODS_TYPE.ACTIVITY);
        List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(query);
        if (CollectionUtils.isNotEmpty(goods)) {
            result.setMessage("该动态已付费");
            return null;
        }
        return activity;
    }

    @GetMapping("get_merchandises_by_date")
    public JsonResult getMerchandiseByDate(@RequestParam("date") String date) {
        PaidBillboard billboardQuery = new PaidBillboard();
        billboardQuery.setStatus(PaidBillboard.PAID);
        billboardQuery.setUserId(getUserId());
        List<PaidBillboard> billboards = corgiBillboardService.queryPaidBillboard(billboardQuery, 1, 1);
        if (CollectionUtils.isNotEmpty(billboards)) {
            return new JsonResult(Constants.BILLBOARD_STATUS, "审核中");
        }
        CorgiMerchandise query = new CorgiMerchandise();
        query.setType("billboard");
        if (this.checkYearBillboard()) {
            query.setType("billboardyear");
        }
        List<CorgiMerchandise> merchandises = corgiOrderService.getMerchandise(query);
        return new JsonResult(merchandises);
    }

    @GetMapping("get_merchandises")
    public JsonResult getMerchandise(@RequestParam("type") String type) {
        CorgiMerchandise query = new CorgiMerchandise();
        query.setType(type);
        if (!"AppStore".equals(RequestUtil.getChannel()) && type.equals(CorgiMerchandise.SUBSCRIBE)) {
            query.setType(type.concat("-android"));
        }
        if (type.startsWith(CorgiMerchandise.SUBSCRIBE)) {
            CorgiUserGoods orderQuery = CorgiUserGoods.builder()
                    .userId(getUserId())
                    .goodsType(CorgiUserGoods.GOODS_TYPE.SUBSCRIBE)
                    .start(0)
                    .size(1)
                    .build();
            List<CorgiUserGoods> orders = corgiOrderService.getUserGoods(orderQuery);
            if (CollectionUtils.isNotEmpty(orders)) {
                query.setDisReason("续费");
            } else {
                query.setDisReason("首购");
            }
        }
        List<CorgiMerchandise> merchandises = corgiOrderService.getMerchandise(query);

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
        if (StringUtils.isEmpty(receiptData)) {
            receiptData = receiptData.replace(" ", "+");
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
                        String appMerchId = inApp.getString("product_id");
                        CorgiMerchandise query = new CorgiMerchandise();
                        query.setAppMerchId(appMerchId);
                        List<CorgiMerchandise> merchandises = corgiOrderService.getMerchandise(query);
                        String merchId = order.getMerchId();

                        if (CollectionUtils.isNotEmpty(merchandises) && merchandises.size() < 3) {
                            CorgiMerchandise lastMerchandise = new CorgiMerchandise();
                            for (CorgiMerchandise merchandise : merchandises) {
                                lastMerchandise = merchandise;
                                if (merchandise.getId().equals(merchId)) {
                                    break;
                                }
                            }
                            order.setMerchId(lastMerchandise.getId());
                            order.setPayAmount(lastMerchandise.getPrice());
                        } else {
                            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "订单信息有误");
                        }
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
        CorgiOrder oldOrder = corgiOrderService.getOrderByTradeNo(order.getTradeNo());
        order.setMerchId(oldOrder.getMerchId());
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
    public JsonResult applepayCallback(@RequestBody JSONObject object) {
        log.info("callback:{} ", object);
        try {
            String payload = object.getString("signedPayload");
            DecodedJWT decodedJWT = JWT.decode(payload);

            String signedPayload = new String(java.util.Base64.getDecoder().decode(payload.split("\\.")[0]));
            JSONObject jsonObject = JSONObject.parseObject(signedPayload);
            String x5c = jsonObject.getJSONArray("x5c").get(0).toString();
            signedPayload = verify(x5c, decodedJWT);
            if (org.apache.commons.lang3.StringUtils.isNotEmpty(signedPayload)) {
                //第一次解密
                String fromBASE64 = getFromBASE64(signedPayload);
                // 解密出来的字符串有时候最后会加特殊符号，所以截取了一下
                fromBASE64 = fromBASE64.substring(fromBASE64.indexOf("{"), fromBASE64.lastIndexOf("}") + 1);
                log.info("苹果订阅回调.BASE64解密拿到数据============" + fromBASE64);
                jsonObject = JSONObject.parseObject(fromBASE64);
                //判断uuid是否重复调用
                String s = JSONObject.parseObject(jsonObject.get("data").toString()).get("signedTransactionInfo").toString();
                //解密拿到数据
                DecodedJWT sd = JWT.decode(s);
                String verify = verify(x5c, sd);
                //线程池
                String fromBASE641 = getFromBASE64(verify);
                //第一标识
                String notificationType = jsonObject.get("notificationType").toString();
                //第二标识
                String subtype = String.valueOf(Optional.ofNullable(jsonObject.get("subtype")).orElse(""));

                fromBASE641 = fromBASE641.substring(fromBASE641.indexOf("{"), fromBASE641.lastIndexOf("}") + 1);
                JSONObject jsonBASE64 = JSONObject.parseObject(fromBASE641);
                corgiOrderService.addLog(jsonBASE64.toJSONString(), jsonBASE64.getString("transactionId"), jsonBASE64.getString("originalTransactionId") + "-" + notificationType);
                if ("DID_RENEW".equals(notificationType)) {
                    CorgiOrder subscribe = new CorgiOrder();
                    subscribe.setOrderId(jsonBASE64.getString("originalTransactionId"));
                    Long expireTime = jsonBASE64.getLong("expiresDate");
                    corgiOrderService.subscribe(subscribe, null, "1", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(expireTime)));
                }
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
        CorgiOrder oldOrder = corgiOrderService.getOrderByTradeNo(order.getTradeNo());
        order.setMerchId(oldOrder.getMerchId());
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

    private HashMap<String, Object> payResult(String payType, String marketId, String sellerId, CorgiMerchandise merchandise) {
        HashMap<String, Object> result = new HashMap<>();
        String tradeNo = UuidUtil.getTradeNo(getUserId());
        CorgiOrder order = CorgiOrder.builder()
                .userId(getUserId())
                .payType(payType)
                .marketId(marketId)
                .desc(MerchandiseEnum.getByCode(merchandise.getId().replaceAll("SA", "S")).getDesc())
                .merchId(merchandise.getId())
                .tradeNo(tradeNo)
                .sellerId(sellerId)
                .payAmount(merchandise.getPrice())
                .packageName(RequestUtil.getPackageName())
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
        return result;
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

    private boolean checkYearBillboard() {
        String vipExpire = corgiUserService.getUserVipExpire(getUserId());
        if (StringUtils.isNotEmpty(vipExpire) && !"-".equals(vipExpire)) {
            List<CorgiUserGoods> goods = corgiOrderService.getUserGoods(CorgiUserGoods.builder()
                    .userId(getUserId())
                    .goodsType(CorgiMerchandise.SUBSCRIBE)
                    .build());
            if (CollectionUtils.isNotEmpty(goods) && Arrays.asList("AS01", "AS05", "S01", "S05").contains(goods.get(0).getMerchId())) {
                CorgiUserGoods yearGoods = goods.get(0);
                PaidBillboard paidBillboard = new PaidBillboard();
                paidBillboard.setStatus("pass");
                paidBillboard.setCtime(yearGoods.getCtime());
                List<PaidBillboard> passBillboards = corgiBillboardService.queryPaidBillboard(paidBillboard, 1, 10);
                if (CollectionUtils.isEmpty(passBillboards)) {
                    paidBillboard.setStatus("fail");
                    List<PaidBillboard> failBillboards = corgiBillboardService.queryPaidBillboard(paidBillboard, 1, 10);
                    return CollectionUtils.isEmpty(failBillboards) || failBillboards.size() < 3;
                }
            }
        }
        return false;
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

    private long getCancelMinutes(List<CorgiOrder> orders) {
        String ctime = "";
        for (CorgiOrder order : orders) {
            if (ctime.compareTo(order.getCtime()) < 0) {
                ctime = order.getCtime();
            }
        }
        Long now = System.currentTimeMillis();
        Long time;
        try {
            time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ctime).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 5l;
        }
        if (now - time > 5 * 60000) {
            return 1;
        }
        return 5 - (now - time) / 60000 + 1;
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

    public static String verify(String x5c0, DecodedJWT decodedJWT) throws CertificateException {
        PublicKey publicKey = getPublicKeyByX5c(x5c0);
        // 验证 token
        Algorithm algorithm = Algorithm.ECDSA256((ECPublicKey) publicKey, null);
        algorithm.verify(decodedJWT);
        String payload = decodedJWT.getPayload();

        return payload;
    }

    public static PublicKey getPublicKeyByX5c(String x5c) throws CertificateException {
        byte[] x5c0Bytes = java.util.Base64.getDecoder().decode(x5c);
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        Certificate cer = fact.generateCertificate(new ByteArrayInputStream(x5c0Bytes));
        return cer.getPublicKey();
    }

    public static String getFromBASE64(String jwt) {
        return new String(java.util.Base64.getDecoder().decode(jwt));
    }
}

