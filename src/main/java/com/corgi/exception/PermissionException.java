package com.corgi.exception;

import javax.servlet.ServletException;

/**
 * @author tairanliu
 */
public class PermissionException extends ServletException {
    public int errorCode;
    public String errorMsg;

    public PermissionException(String errorMsg) {
        super(errorMsg);
    }

    public PermissionException(int errorCode, String errorMsg) {
        super(errorMsg);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }
}
