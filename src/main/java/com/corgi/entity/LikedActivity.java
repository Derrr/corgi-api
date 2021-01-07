package com.corgi.entity;

import lombok.Data;

@Data
public class LikedActivity {
    private String picUrl;
    private String activityId;
    private String category;
    private Integer height = 0;
    private Integer width = 0;
}
