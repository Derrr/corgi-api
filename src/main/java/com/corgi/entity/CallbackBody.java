package com.corgi.entity;

import lombok.Data;

@Data
public class CallbackBody  {
    private String EventTime;
    private String EventType;
    private String JobId;
    private String MediaId;
    private String Status;
    private String Code;
    private String Message;
    private AIMediaData Data;
}
