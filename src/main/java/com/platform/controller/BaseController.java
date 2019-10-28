package com.platform.controller;

import com.platform.common.util.RequestUtil;

public class BaseController {
    protected String getCode() {
        return RequestUtil.getRequest().getHeader("Token");
    }

    protected String getAppId() {
        return (String) RequestUtil.getRequest().getAttribute("APP_ID");
    }

    protected String getAppKey() {
        return (String) RequestUtil.getRequest().getAttribute("APP_Key");
    }

    protected String getAppSecret() {
        return (String) RequestUtil.getRequest().getAttribute("APP_Secret");
    }

    protected String getUserId() {
        return RequestUtil.getUserId();
    }

    protected String getUserName() {
        LoginUser loginUser = (LoginUser) RequestUtil.getRequest().getAttribute("login_user");
        return loginUser.getNickname();
    }

    protected LoginUser getUser() {
        return (LoginUser) RequestUtil.getRequest().getAttribute("login_user");
    }

    protected String getStoreId() {
        return RequestUtil.getRequest().getParameter("store_id");
    }
}
