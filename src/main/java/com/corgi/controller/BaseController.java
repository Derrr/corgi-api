package com.corgi.controller;

import com.corgi.common.util.RequestUtil;

public class BaseController {

    protected String getUserId() {
        return RequestUtil.getUserId();
    }

}
