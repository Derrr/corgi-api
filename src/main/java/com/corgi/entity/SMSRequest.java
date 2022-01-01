package com.corgi.entity;

import lombok.Data;

@Data
public class SMSRequest {
    private String packageName;
    private String sceneCode;
    private String osType;
    private String bundleId;
    private String telNo;
}
