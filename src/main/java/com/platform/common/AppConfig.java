package com.platform.common;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.*;
import java.util.HashMap;

/**
 * APP_ID和CLIENT对应关系
 * @author tairanliu
 */
@Component
public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);

    @Value("${app.props}")
    private String appPropsPath;

    private static HashMap<String, AppClientInfo> config;


    private void initAppConfig() {
        config = new HashMap<>();
        StringBuffer sb = new StringBuffer();
        String tempstr = null;
        try {
            File file = ResourceUtils.getFile(appPropsPath);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }
            FileInputStream fis = new FileInputStream(file);
            BufferedReader br = new BufferedReader(new InputStreamReader(fis));
            while ((tempstr = br.readLine()) != null) {
                sb.append(tempstr + "\r\n");
            }

            JSONArray jsonArray = JSONArray.fromObject(sb.toString());
            for (int i = 0; i < jsonArray.size(); i++) {

                JSONObject object = jsonArray.getJSONObject(i);
                AppClientInfo clientInfo = new AppClientInfo();
                clientInfo.setAppName(object.getString("appName"));
                clientInfo.setClientId(object.getString("clientId"));
                clientInfo.setClientSecret(object.getString("clientSecret"));
                clientInfo.setJwtPublicKey(object.optString("jwtPublicKey"));
                JSONArray routerJson = object.getJSONArray("routers");
                String[] routers = new String[routerJson.size()];
                for (int j = 0; j < routerJson.size(); j++) {
                    routers[j] = routerJson.getString(j);
                }
                clientInfo.setRouters(routers);

                config.put(object.getString("appId"), clientInfo);
            }
        } catch (Exception e) {
            logger.error("", e);
        }

    }

    public HashMap<String, AppClientInfo> getConfig() {
        return config;
    }

    public AppClientInfo getClientInfoByAppid(String appId) {
        if (config == null) {
            initAppConfig();
        }

        return config.get(appId);
    }
}
