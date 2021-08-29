package com.corgi.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author tairanliu
 */
@Data
public class UserData {
    public UserData(String userId, Integer followCount, Integer fanCount, Integer getLikedCount) {
        this.userId = userId;
        this.followCount = followCount;
        this.fanCount = fanCount;
        this.getLikedCount = getLikedCount;
    }

    private String userId;
    private Integer followCount;
    private Integer fanCount;
    private Integer getLikedCount;
}
