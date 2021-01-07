package com.corgi.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author tairanliu
 */
@Data
public class PageResult<T> implements Serializable {
    private static final long serialVersionUID = -4699713095477152084L;

    private Integer tPage;

    public Integer getTPage() {
        return tPage;
    }

    public Integer getDPage() {
        return dPage;
    }

    private Integer dPage;
    private T data;
    private int code;
    private String message = CorgiConstants.SUCCESS;


    public PageResult() {
        this.code = 0;
    }

    public PageResult(T data, int code, String msg) {
        this.data = data;
        this.message = msg;
        this.code = code;
    }

    public PageResult(int code, String msg) {
        this.message = msg;
        this.code = code;
    }

    public PageResult(T data, String msg) {
        this.data = data;
        this.message = msg;
        this.code = 0;
    }

    public PageResult(T data, Integer tPage, Integer dPage) {
        this.data = data;
        this.code = 0;
        this.dPage = dPage;
        this.tPage = tPage;
    }

    @Override
    public String toString() {
        return "PageResult{" +
                "data=" + data +
                ", tPage=" + tPage +
                ", dPage=" + dPage +
                ", code=" + code +
                ", msg='" + message + '\'' +
                '}';
    }
}
