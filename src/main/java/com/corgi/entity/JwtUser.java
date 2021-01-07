package com.corgi.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author tairanliu
 */
@Data
@Builder
public class JwtUser {
    public static final String USER_ID = "userId";
    public static final String VERSION = "version";

    private String userId;
    private String version;
}
