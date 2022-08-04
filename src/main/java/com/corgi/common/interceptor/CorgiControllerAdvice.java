package com.corgi.common.interceptor;

import org.apache.dubbo.rpc.RpcException;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.exception.PermissionException;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * @author tairanliu
 * @create 2019-03-15
 */
@ControllerAdvice
public class CorgiControllerAdvice {

    private static final Logger logger = LogManager.getLogger(CorgiControllerAdvice.class);

    /**
     * 应用到所有@RequestMapping注解方法，在其执行之前初始化数据绑定器
     *
     * @param binder
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
    }

    /**
     * 把值绑定到Model中，使全局@RequestMapping可以获取到该值
     *
     * @param model
     */
    @ModelAttribute
    public void addAttributes(Model model) {
    }


    @ExceptionHandler(PermissionException.class)
    @ResponseBody
    public ResponseEntity handleControllerPermissionException(HttpServletRequest request, PermissionException ex) {
        logger.error("Permission exception:" + ex.getMessage(), ex);
        JsonResult jsonResult = new JsonResult("");
        jsonResult.setCode(ex.errorCode);
        jsonResult.setMessage(ex.getMessage());
        jsonResult.setData(request.getParameterMap());
        return new ResponseEntity(jsonResult, HttpStatus.OK);
    }

    @ExceptionHandler(RpcException.class)
    @ResponseBody
    public ResponseEntity handleControllerIllegalStateException(HttpServletRequest request, Throwable ex) {
        String errorMsg = ex.getMessage();
        if (!StringUtils.isEmpty(errorMsg)) {
            errorMsg = errorMsg.replaceAll(System.getProperty("line.separator"), "");
        }
        logger.error(errorMsg, ex);
        JsonResult jsonResult = new JsonResult("");
        jsonResult.setCode(Constants.SERVER_ERROR_CODE);
        jsonResult.setMessage("server fail");
        jsonResult.setData(request.getParameterMap());
        return new ResponseEntity(jsonResult, HttpStatus.OK);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity handleControllerRuntimeException(HttpServletRequest request, Throwable ex) {
        String errorMsg = ex.getMessage();
        if (!StringUtils.isEmpty(errorMsg)) {
            errorMsg = errorMsg.replaceAll(System.getProperty("line.separator"), "");
        }
        logger.error(errorMsg, ex);
        JsonResult jsonResult = new JsonResult("");
        jsonResult.setCode(Constants.SYS_ERROR_CODE);
        jsonResult.setMessage(ex.getMessage());
        jsonResult.setData(request.getParameterMap());
        return new ResponseEntity(jsonResult, HttpStatus.OK);
    }
}
