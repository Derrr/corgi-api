package com.corgi.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class ErnieBotService {

    public static final String API_KEY = "Bt64y1zy1VUVsRxrrUSypEKB";
    public static final String SECRET_KEY = "19N8q14nSTVSENiSSwoF66CLzov5cf0z";

    static final OkHttpClient HTTP_CLIENT = new OkHttpClient().newBuilder()
            .callTimeout(120, TimeUnit.SECONDS)
            .pingInterval(5, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .build();

    public String getMessage(String userId, String system, String message) {
        JSONObject messagebody = new JSONObject();
        messagebody.put("user_id", userId);
        messagebody.put("system", system);

        JSONArray messageContext = new JSONArray();
        JSONObject userObj = new JSONObject();
        userObj.put("role", "user");
        userObj.put("content", message);
        messageContext.add(userObj);

        messagebody.put("messages", messageContext);

        MediaType mediaType = MediaType.parse("application/json");
        RequestBody body = RequestBody.create(mediaType, messagebody.toJSONString());
        Request request = null;
        try {
            request = new Request.Builder()
                    .url("https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions_pro?access_token=" + getAccessToken())
                    .method("POST", body)
                    .addHeader("Content-Type", "application/json")
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
            return "获取accesstokey故障: " + e.getMessage();
        }
        Response response = null;
        try {
            response = HTTP_CLIENT.newCall(request).execute();
            return JSONObject.parseObject(response.body().string()).getString("result");
        } catch (IOException e) {
            e.printStackTrace();
            if (response != null && response.body() != null) {
                return "系统故障: " + response.body();
            }
            return "系统故障: " + e.getMessage();
        }
    }


    /**
     * 从用户的AK，SK生成鉴权签名（Access Token）
     *
     * @return 鉴权签名（Access Token）
     * @throws IOException IO异常
     */
    public static String getAccessToken() throws IOException {
        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        RequestBody body = RequestBody.create(mediaType, "grant_type=client_credentials&client_id=" + API_KEY
                + "&client_secret=" + SECRET_KEY);
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();
        Response response = HTTP_CLIENT.newCall(request).execute();
        return JSONObject.parseObject(response.body().string()).getString("access_token");
    }

}

