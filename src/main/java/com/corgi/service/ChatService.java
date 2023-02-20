package com.corgi.service;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.*;
import com.aliyun.teaopenapi.models.Config;
import com.corgi.common.util.CorgiHttpUtil;
import com.corgi.entity.SMSRequest;
import com.google.gson.Gson;
import com.unfbx.chatgpt.OpenAiClient;
import com.unfbx.chatgpt.entity.completions.CompletionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class ChatService {

    private String apiKey = "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";
    private String host = "https://api.openai.com/v1/completions";


    public String prompt(String text) {
        return CorgiHttpUtil.doPost(host, this.getBody(text.concat("\n")), getHeader());
//        OpenAiClient openAiClient = new OpenAiClient(apiKey, 60, 60, 60);
//        CompletionResponse completions = openAiClient.completions(text.concat("\n"));
//        return completions.getChoices()[0].getText();
    }

    private Map<String, Object> getBody(String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "text-davinci-003");
        body.put("prompt", text);
        body.put("temperature", 0.0);
        body.put("top_p", 1);
        body.put("n", 1);
        body.put("stream", false);
        body.put("echo", false);
        body.put("stop", Arrays.asList("#"));
        body.put("frequency_penalty", 0.0);
        body.put("presence_penalty", 0.0);
        body.put("best_of", 1);
        body.put("max_tokens", 2048);
        return body;
    }

    private Map<String, String> getHeader() {
        Map<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/json");
        header.put("Authorization", "Bearer ".concat(apiKey));
        return header;
    }
}
