package com.corgi.entity;

import lombok.Data;

@Data
public class LikedActivity {
    private String picUrl;
    private String activityId;
    private String category;
    private Long height = 0L;
    private Long width = 0L;
}
