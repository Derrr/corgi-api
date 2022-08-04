package com.corgi.common.util;

import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.http.Consts;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.CodingErrorAction;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author tairanliu
 */
public class CorgiHttpUtil {
    private static final Integer MAX_TOTAL = 200;
    private static final Integer MAX_PERROUTE = 50;
    private static Logger logger = LogManager.getLogger(CorgiHttpUtil.class);
    private static PoolingHttpClientConnectionManager CM = null;
    private static SocketConfig SOCKET_CONFIG = null;
    private static ConnectionConfig CONNECTION_CONFIG = null;
    private static RequestConfig REQUEST_CONFIG = null;


    static {
        if (CM == null) {
            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
            cm.setMaxTotal(MAX_TOTAL);
            cm.setDefaultMaxPerRoute(MAX_PERROUTE);
            CM = cm;
        }
        //socket配置
        if (SOCKET_CONFIG == null) {
            SocketConfig socketConfig = SocketConfig.custom()
                    .setTcpNoDelay(true)
                    .setSoReuseAddress(true)
                    .setSoLinger(60)
                    .setSoKeepAlive(true)
                    .build();
            SOCKET_CONFIG = socketConfig;
        }
        if (CONNECTION_CONFIG == null) {
            //消息约束
            MessageConstraints messageConstraints = MessageConstraints.custom()
                    .setMaxHeaderCount(200)
                    .setMaxLineLength(2000)
                    .build();
            //connection配置
            ConnectionConfig connectionConfig = ConnectionConfig.custom()
                    .setMalformedInputAction(CodingErrorAction.IGNORE)
                    .setUnmappableInputAction(CodingErrorAction.IGNORE)
                    .setCharset(Consts.UTF_8)
                    .setMessageConstraints(messageConstraints)
                    .build();
            CONNECTION_CONFIG = connectionConfig;
        }


        //request配置
        if (REQUEST_CONFIG == null) {
            RequestConfig defaultRequestConfig = RequestConfig.custom()
                    .setConnectTimeout(2000)
                    .setSocketTimeout(5000)
                    .setConnectionRequestTimeout(500)

                    .build();
            REQUEST_CONFIG = defaultRequestConfig;
        }
    }

    public static CloseableHttpClient getHttpClient() {
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultSocketConfig(SOCKET_CONFIG)
                .setDefaultConnectionConfig(CONNECTION_CONFIG)
                .setDefaultRequestConfig(REQUEST_CONFIG)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true))
                .setConnectionManager(CM).build();
        return httpClient;
    }

    public static String doGet(String url, List<NameValuePair> queryParams, Map<String, String> headers) {
        Date startTime = new Date();
        HttpGet request = null;
        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            if (!CollectionUtils.isEmpty(queryParams)) {
                uriBuilder.addParameters(queryParams);
            }
            request = new HttpGet(uriBuilder.build());
        } catch (URISyntaxException e) {
            logger.error(e.getMessage(), e);
        }

        StringBuffer log = new StringBuffer("输入：{ <URL: " + request.getURI() + "> , <Method: doGet> , <Http Header: " + headers + "> }");

        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }

        CloseableHttpResponse response = null;
        String result = "";
        try {
            response = getHttpClient().execute(request);
            // 判断网络连接状态码是否正常(0--200都数正常)
            if (response != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                result = EntityUtils.toString(response.getEntity(), "utf-8");
                InputStream in = response.getEntity().getContent();
                in.close();
            } else if (response != null) {
                logger.error("请求" + url + "获取失败, 状态异常：" + response.getStatusLine().getStatusCode());
                result = EntityUtils.toString(response.getEntity(), "utf-8");
            }
        } catch (IOException e) {
            logger.error("请求地址出错," + url + "错误信息:", e);
            request.abort();
        } catch (IllegalArgumentException e) {
            logger.error("返回参数错误", e);
            request.abort();
        } finally {
            if (response != null) {
                try {

                    response.close();
                } catch (IOException e) {
                    logger.error("请求地址关闭出错," + url + "错误信息:", e);
                }
            }
        }

        Date endTime = new Date();
        long executeTime = endTime.getTime() - startTime.getTime();
        logger.info("外部接口调用时间：" + executeTime + "ms");
        logger.info(log.toString());
        logger.info("输出：{" + result + "}");

        return result;
    }
}
