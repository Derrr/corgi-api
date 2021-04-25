package com.corgi.controller;

import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.RequestUtil;
import org.springframework.web.bind.annotation.RequestBody;
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

    public boolean hasVersion() {
        if (RequestUtil.hasVersion()) {
            String version = RequestUtil.getVersion();
            if (version.startsWith("android")) {
                return version.compareTo("android1.8.0") >= 0;
            } else {
                return version.compareTo("1.8.0") >= 0;
            }
        }
        return false;
    }

    public String getVersion() {
        return RequestUtil.getVersion();
    }


    public boolean hasUserId() {
        return RequestUtil.hasUserId();
    }

}
