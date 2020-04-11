package com.corgi.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author tairanliu
 */
@Data
@Builder
public class JwtUser {
    private String userId;
    private String version;
}
