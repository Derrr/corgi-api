package com.corgi.entity;

import lombok.Builder;
import lombok.Data;

/**
 * @author tairanliu
 */
@Data
@Builder
public class SignUpUser {
    private String signUpUserId;
    private String signUpUserName;
    private String signUpUserAvatar;
}
