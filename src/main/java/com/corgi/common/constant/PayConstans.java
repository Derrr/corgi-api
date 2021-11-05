package com.corgi.common.constant;

public interface PayConstans {
    String SUCCESS = "success";
    String CLOSE = "close";
    String FAIL = "fail";

    interface ALIPAY {
        String TRADE_FINISHED = "TRADE_FINISHED";
        String TRADE_SUCCESS = "TRADE_SUCCESS";
        String TRADE_CLOSED = "TRADE_CLOSED";
    }

    interface WX {
        String SUCCESS = "SUCCESS";
        String FAIL = "FAIL";
    }
}
