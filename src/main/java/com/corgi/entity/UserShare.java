package com.corgi.entity;

import com.corgi.user.entity.BarProfile;
import com.corgi.user.entity.UserProfile;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserShare implements Serializable {
    private Integer matchCount;
    private Integer followCount;
    private List<UserProfile> matchUsers = new ArrayList<>();
    private List<UserProfile> followUsers = new ArrayList<>();

    public Integer getMatchCount() {
        return matchUsers.size();
    }

    public Integer getFollowCount() {
        return followUsers.size();
    }

}
