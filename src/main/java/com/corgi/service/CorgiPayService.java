package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeAppPayModel;
import com.alipay.api.request.AlipayTradeAppPayRequest;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeAppPayResponse;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.corgi.common.wxpay.sdk.CorgiWXPayConfig;
import com.corgi.common.wxpay.sdk.WXPay;
import com.corgi.common.wxpay.sdk.WXPayConstants;
import com.corgi.common.wxpay.sdk.WXPayUtil;
import com.corgi.user.api.CorgiOrderService;
import com.corgi.user.entity.CorgiMerchandise;
import com.corgi.user.entity.CorgiOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class CorgiPayService {
    private static WXPay wxPay = new WXPay();
    @Reference
    private CorgiOrderService corgiOrderService;
    @Autowired
    private RestTemplate restTemplate;

    public static final String ALI_URL = "https://openapi.alipay.com/gateway.do";
    /**
     * 应用id，如何获取请参考：https://opensupport.alipay.com/support/helpcenter/190/201602493024
     **/
    public static final String APP_ID = "2021002164661374";
    /**
     * 应用私钥，如何获取请参考：https://opensupport.alipay.com/support/helpcenter/207/201602469554
     **/
    public static final String APP_PRIVATE_KEY = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCpy+7TbBdGVhbaQhAOkYk1b4zKjH2U5sNQgnq9c/fI74ynHqCTXox8t95e171GIN1H5YonnT1RHq/UE8FnKcvRCkNDswkpo8ILAf0KKlFgfN1kqQrfJK9xST98g1CKU+fZ2WWIk7bgyMrs6VEWa09KJdb4um8YAnUvb3eDT91CgCMpFiIIpMl9TBiN0+qcG+2ORtJrJmCo8QveUpZtOl3DwO28jv+4YV5iXXgdv0TtILVQSU/QnGtmIXzbPcCAD9FuLbOLnP3HJNRpGmWO1vkQhLmzdVD9n3Y0OB6BzjBvtWSB18/oC9vmAU+vF60/OaLxWOWNQ0LFU5EcjyVHAMifAgMBAAECggEAKLxnaMu27cX7p5NP3N7npy1C/tkjy9RtKWSUY91tpgRqnzGG3rRBSi6mp+RkYW3DCNu2AHkF2+9byaqPrNtnLZijuJs8aIQEKrXoakbqzRZH2z1/ATgA61HibFHowbcNmcNBS7n8lwM1RA9Zx+Io3KYlY/j+bCkyyhWY+6TudWR5JxmM32wQBMASbf8d1af8xDXTGQSKXF+1qPM9E5nL+VxiNU/wJJ5MszKlvQApxKyVZ+3bZ8r66/qtn2GwNq1yzX+CaYfyffHz2uk1PfzTqM3tNqszHAXKFgBgoZs9tbNM+uNnxDrPnnuDCA5Tlam4nPbLikiA9yTACMyNoTbBQQKBgQD1r+gl6cy0btVVAuHJA3bRQzdMgOxaToXyqiUFfMb+xSPyF31m5CxM+P+IeUM9R3kE7PmchXc73aPf6HLMEAIrJVnK0AlE/yFbDa8gDWyW/Ll7NyJuXVp7P72UsbgVWqjBpEqUp3LTOeXlsobl+UjAWAml4jgNlO5MJC6Zr7XCIQKBgQCw7IZ6MQgwIuVtyPRhgaASkdg5jjUV0odUp42UeZx9X76+CgOWT/3A1zs9jppukUfaQa9Z2LnotSiL9ya54dIATtB9yuzMsh+cxb0aC8chZTnxvUxLcfcmsQgVK3Iw4nYdi9v1wD0C2tmozyIdx2z/V20qpZ8G451oDHnGdCqyvwKBgQCsUZqTrO4kx2/dVk4ifMmDcI+CmxIrLNQKJYgd1yyDWKYjkJIl7neb7TDc+aBNhKm+6K8SNxIv7P6ZdyG9OqUqueHGvC8kM4WjpW9lHcVCCTPW1g7SNavWshg4CIZCg/nFB4Q/y0pgGEXE23h+KF/8eEMcFBSYghK5WM9Of80NwQKBgQCDF77M62fVwwWcwznQxeuF1usQOn67HLOJ1lzhlvqNK1R6G5Fs3vh22wPaKL/lDWDgJ6t2N1AJTbItg4P+V4TzFXMGwkWTpqgl0Z68nd1+sTKuHEVb4aXv1VzX0slZz3MVkXv6K+cJJoAAxPnSduIckPsijnW29RC8+AGDOrAooQKBgFgrTJs8AjuVORcbJbYGT9r9LKpZQJHbyi6ClQWQxN9uytBHIdN5+CZnTL9TtItPhDz7p20JLOSUwWufBTmcG6GJvjCE7/vWlny/trIgNJwe/Bjn4mqezhJRCaE5T0IcRo6q4JSQASuc8Sb7Zx/UGBLSMc32MAZVLT3KS+U1lzI/";

    /**
     * 支付宝公钥，如何获取请参考：https://opensupport.alipay.com/support/helpcenter/207/201602487431
     **/
    //String APP_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqcvu02wXRlYW2kIQDpGJNW+Myox9lObDUIJ6vXP3yO+Mpx6gk16MfLfeXte9RiDdR+WKJ509UR6v1BPBZynL0QpDQ7MJKaPCCwH9CipRYHzdZKkK3ySvcUk/fINQilPn2dlliJO24MjK7OlRFmtPSiXW+LpvGAJ1L293g0/dQoAjKRYiCKTJfUwYjdPqnBvtjkbSayZgqPEL3lKWbTpdw8DtvI7/uGFeYl14Hb9E7SC1UElP0JxrZiF82z3AgA/Rbi2zi5z9xyTUaRpljtb5EIS5s3VQ/Z92NDgegc4wb7VkgdfP6Avb5gFPrxetPzmi8VjljUNCxVORHI8lRwDInwIDAQAB";
    public static final String ALIPAY_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxPhoJ5557xk1l5k9zLslYbTYb0TfO/c+51FyV+wN4F7VCadppxrOLdEean23gYw4+6qM4A3LvNBCptnrXuRlyb80j0pbYK8hPf5l8i5VSwERXf72kbOxwgKGZPurbBSvZM+QWXxY8yrjXbHqTNKJxIhIAVDWSJqxB2KPtxeJZlylJ1YhUfSEoxbYHKeW7JJWaZzzuVfYkCwAdWFX0wKAAeEVlXmD8dAjwvXjD4a0JFrQJFT7w2fsZXbiFjdu3ufcQzGZUw4oJ5wHwMrblcOPhYmUDbbRgriVUU9VpoMfXdK8LBUtnC3UXHz823XPEgIsXniCSKXp23r+iJxi9jA+0wIDAQAB";

    public void alipayCloseOrder(CorgiOrder order) throws AlipayApiException {
        AlipayClient alipayClient = new DefaultAlipayClient(ALI_URL, APP_ID, APP_PRIVATE_KEY, "json", "UTF-8", ALIPAY_PUBLIC_KEY, "RSA2");
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("trade_no", "2013112611001004680073956707");
        request.setBizContent(bizContent.toString());
        AlipayTradeCloseResponse response = alipayClient.execute(request);
        if (response.isSuccess()) {
            order.setResult("订单关闭");
            order.setStatus(CorgiOrder.STATUS.CLOSE);
        } else {
            order.setResult(response.getMsg());
        }
    }

    public String getAlipayOrder(CorgiMerchandise merchandise, CorgiOrder order) {

        corgiOrderService.addOrder(order);

        AlipayClient alipayClient = new DefaultAlipayClient(ALI_URL, APP_ID, APP_PRIVATE_KEY, "json", "UTF-8", ALIPAY_PUBLIC_KEY, "RSA2");
        AlipayTradeAppPayRequest request = new AlipayTradeAppPayRequest();
        AlipayTradeAppPayModel model = new AlipayTradeAppPayModel();
        model.setOutTradeNo(order.getTradeNo());
        model.setSubject(merchandise.getTitle());
        model.setProductCode("QUICK_MSECURITY_PAY");
        model.setTotalAmount(merchandise.getPrice() + "");
        model.setBody(merchandise.getContent());
        request.setBizModel(model);
        request.setNotifyUrl("https://api.corgi.org.cn/order/alipay_callback");
        //request.setNotifyUrl("http://139.224.63.240:7888/order/alipay_callback");
        try {
            AlipayTradeAppPayResponse response = alipayClient.sdkExecute(request);
            return response.getBody();
        } catch (AlipayApiException e) {
            log.error(e.getErrMsg(), e);
            return e.getErrMsg();
        }
        /** response.getBody()打印结果就是orderString，可以直接给客户端请求，无需再做处理。 如果传值客户端失败，可根据返回错误信息到该文档寻找排查方案：https://opensupport.alipay.com/support/helpcenter/89 **/
    }

    public Map<String, String> wxRefundOrder(CorgiOrder order) throws Exception {
        Map<String, String> orderMap = new HashMap<>();
        String refundFee = new Double(order.getPayAmount() * 100).intValue() + "";
        orderMap.put("out_trade_no", order.getTradeNo());
        orderMap.put("out_refund_no", "r" + order.getTradeNo());
        orderMap.put("total_fee", refundFee);
        orderMap.put("refund_fee", refundFee);
        Map<String, String> result = wxPay.refund(orderMap);
        order.setResult("订单退款");
        order.setStatus(CorgiOrder.STATUS.PROCESSING);
        return result;
    }

    public Map<String, String> wxCloseOrder(CorgiOrder order) throws Exception {
        Map<String, String> orderQuery = new HashMap<>();
        orderQuery.put("out_trade_no", order.getTradeNo());
        Map<String, String> result = wxPay.closeOrder(orderQuery);
        order.setResult("订单关闭");
        order.setStatus(CorgiOrder.STATUS.CLOSE);
        return result;
    }

    public Map<String, String> getWXPayOrder(CorgiMerchandise merchandise, CorgiOrder order) {
        corgiOrderService.addOrder(order);

        Map<String, String> body = new HashMap<>();
        body.put("body", merchandise.getTitle());
        body.put("out_trade_no", order.getTradeNo());
        body.put("notify_url", "https://api.corgi.org.cn/order/wx_callback");
        body.put("total_fee", (long) (merchandise.getPrice() * 100) + "");
        body.put("trade_type", "APP");
        try {
            Map<String, String> response = wxPay.unifiedOrder(body);
            Map<String, String> result = new HashMap<>();
            result.put("appid", response.get("appid"));
            result.put("partnerid", response.get("mch_id"));
            result.put("timestamp", System.currentTimeMillis() / 1000 + "");
            result.put("noncestr", response.get("nonce_str"));
            result.put("prepayid", response.get("prepay_id"));
            result.put("package", "Sign=WXPay");
            result.put("sign", WXPayUtil.generateSignature(result, CorgiWXPayConfig.config.getKey()));
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new HashMap<>();
    }

    public JSONObject verifyApplePay(String receipt, String password) {
        String url = "https://buy.itunes.apple.com/verifyReceipt";
        return verifyApplePay(url, receipt, password);
    }

    public JSONObject verifyApplePay(String url, String receipt, String password) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
            headers.setContentType(type);
            headers.add("Accept", MediaType.APPLICATION_JSON.toString());

            JSONObject param = new JSONObject();
            param.put("receipt-data", receipt);
            if (!StringUtils.isEmpty(password)) {
                param.put("password", password);
                param.put("exclude-old-transactions", true);
            }
            log.info("request：{} ", param.toJSONString());
            HttpEntity<String> formEntity = new HttpEntity(param.toJSONString(), headers);
            String resultStr = restTemplate.postForObject(url, formEntity, String.class);
//            HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
//            connection.setRequestMethod("POST");
//            connection.setDoOutput(true);
//            connection.setAllowUserInteraction(false);
//            PrintStream ps = new PrintStream(connection.getOutputStream());
//            if (!StringUtils.isEmpty(password)) {
//                ps.print("{\"receipt-data\": \"" + receipt + "\",\"password\": \"" + password + "\"}");
//            } else {
//                ps.print("{\"receipt-data\": \"" + receipt + "\"}");
//            }
//            ps.close();
//            BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
//            String str;
//            StringBuffer sb = new StringBuffer();
//            while ((str = br.readLine()) != null) {
//                sb.append(str);
//            }
//            br.close();
//            String resultStr = sb.toString();
            JSONObject result = JSONObject.parseObject(resultStr);
            if (result != null && result.getInteger("status") == 21007) {   //递归，以防漏单
                return verifyApplePay("https://sandbox.itunes.apple.com/verifyReceipt", receipt, password);
            }
            return result;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;

    }

    public void queryAlipayOrder(CorgiOrder order) {
        AlipayClient alipayClient = new DefaultAlipayClient(ALI_URL, APP_ID, APP_PRIVATE_KEY, "json", "GBK", ALIPAY_PUBLIC_KEY, "RSA2");
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", order.getTradeNo());
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
            order.setResult(JSON.toJSONString(JSONObject.toJSONString(response)));
            if (response.isSuccess()) {
                order.setBuyerId(response.getBuyerLogonId());
                if (response.getSendPayDate() != null) {
                    order.setPayTime(sdf.format(response.getSendPayDate()));
                }
                String tradeStatus = response.getTradeStatus();
                if ("TRADE_FINISHED".equals(tradeStatus) || "TRADE_SUCCESS".equals(tradeStatus)) {
                    order.setStatus(CorgiOrder.STATUS.SUCCESS);
                } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                    order.setStatus(CorgiOrder.STATUS.CLOSE);
                } else {
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.MINUTE, -5);
                    if (order.getCtime().compareTo(sdf.format(calendar.getTime())) < 0) {
                        AlipayTradeCloseRequest closeRequest = new AlipayTradeCloseRequest();
                        closeRequest.setBizContent(bizContent.toString());
                        alipayClient.execute(closeRequest);
                    }
                }
            }
        } catch (AlipayApiException e) {
            log.error(e.getErrMsg(), e);
            order.setResult(e.getErrMsg());
        }
        corgiOrderService.updateOrder(order);
    }

    public void queryWXOrder(CorgiOrder order) {
        Map<String, String> orderQuery = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        orderQuery.put("out_trade_no", order.getTradeNo());
        try {
            Map<String, String> result = wxPay.orderQuery(orderQuery);
            order.setResult(JSON.toJSONString(result));
            if (WXPayConstants.FAIL.equals(result.get("return_code"))
                    || WXPayConstants.FAIL.equals(result.get("result_code"))) {
                corgiOrderService.updateOrder(order);
                return;
            }
            String tradeStatus = result.get("trade_state");
            order.setPayTime(result.get("time_end"));
            order.setBuyerId(result.get("openid"));
            if ("SUCCESS".equals(tradeStatus)) {
                order.setStatus(CorgiOrder.STATUS.SUCCESS);
            } else if ("CLOSED".equals(tradeStatus)) {
                order.setStatus(CorgiOrder.STATUS.CLOSE);
            } else if ("PAYERROR".equals(tradeStatus)) {
                order.setStatus(CorgiOrder.STATUS.FAIL);
            } else {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MINUTE, -5);
                if (order.getCtime().compareTo(sdf.format(calendar.getTime())) < 0) {
                    wxPay.closeOrder(orderQuery);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            order.setResult(e.getMessage());
        }
        corgiOrderService.updateOrder(order);
    }

}
