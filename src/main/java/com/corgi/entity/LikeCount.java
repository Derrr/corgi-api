package com.corgi.entity;

import com.corgi.user.entity.ActivityLike;
import lombok.Data;

import java.util.List;

@Data
public class LikeCount {
    private Long count;
    private List<ActivityLike> users;
}
