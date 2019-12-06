package com.corgi.entity;

import lombok.Data;

/**
 * @author tairanliu
 */
@Data
public class StorageToken {
    private String bucketName;
    private String endpoint;
    private String securityToken;
    private String accessKeySecret;
    private String accessKeyId;
    private String expiration;
}
