package com.platform.exception;

public class APIException extends RuntimeException {
    public int errorCode;
    public String errorMsg;

    public APIException(String errorMsg) {
        super(errorMsg);
    }

    public APIException(int errorCode, String errorMsg) {
        super(errorMsg);

        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }
}
