package com.corgi.controller;

import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.RequestUtil;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * @author tairanliu
 */
public class BaseController {
    public JsonResult getJsonResult(String msg) {
        if (CorgiConstants.SUCCESS.equals(msg)) {
            return new JsonResult();
        } else {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, msg);
        }
    }

    public String getUserId() {
        return RequestUtil.getUserId();
    }

    public boolean hasUserId() {
        return RequestUtil.hasUserId();
    }

}
