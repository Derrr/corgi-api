package com.corgi.entity;

import com.corgi.user.entity.UserPic;
import lombok.Data;

import java.util.List;

@Data
public class DateDetail {
    private String userId;
    private String imId;
    private String nickname;
    private String avatar;
    private String avatarCheckStatus;
    private Double match;
    private Double lat;
    private Double lng;
    int isFollowed;
    List<UserPic> userPics;
}
