package com.corgi.common;

import lombok.Data;

import java.io.Serializable;

/**
 * @author tairanliu
 */
@Data
public class JsonResult<T> implements Serializable{
	private static final long serialVersionUID = -4699713095477151084L;
	
	private T data;
	private int code;
	private String message;

	
	public JsonResult(T data, int code, String msg) {
		this.data = data;
		this.message = msg;
		this.code = code;
	}
	
	public JsonResult(T data, String msg) {
		this.data = data;
		this.message = msg;
		this.code = 0;
	}
	
	public JsonResult(T data) {
		this.data = data;
		this.code = 0;
		
	}

	@Override
	public String toString() {
		return "JsonResult{" +
				"data=" + data +
				", code=" + code +
				", msg='" + message + '\'' +
				'}';
	}
}
