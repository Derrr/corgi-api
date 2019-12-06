package com.corgi.controller;

import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;

/**
 * @author tairanliu
 */
public class BaseController {
    public JsonResult getJsonResult(String msg){
        if (CorgiConstants.SUCCESS.equals(msg)) {
            return new JsonResult();
        } else {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, msg);
        }
    }

}
