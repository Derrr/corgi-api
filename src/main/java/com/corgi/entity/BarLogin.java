package com.corgi.entity;

import com.corgi.user.entity.BarProfile;
import lombok.Data;

@Data
public class BarLogin extends BarProfile {
    private String jwt;
}
