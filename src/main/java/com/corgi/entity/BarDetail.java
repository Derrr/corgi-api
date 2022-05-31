package com.corgi.entity;

import com.corgi.user.entity.BarProfile;
import lombok.Data;

import java.util.List;

@Data
public class BarDetail extends BarProfile {
    private Integer count;
    private List<String> avatars;
}
