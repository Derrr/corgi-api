package com.corgi.entity;

import com.corgi.user.entity.UserDetail;
import lombok.Data;

@Data
public class UserMatchProfile extends UserDetail {
    private String isFollowed;
}
