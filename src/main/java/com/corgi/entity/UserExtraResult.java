package com.corgi.entity;

import com.alibaba.fastjson.JSONArray;
import com.corgi.user.entity.UserExtra;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class UserExtraResult {
    public UserExtraResult() {
        super();
    }

    public UserExtraResult(UserExtra userExtra) {
        this.profession = userExtra.getProfession();
        this.aim = userExtra.getAim();
        this.income = userExtra.getIncome();
        this.education = userExtra.getEducation();
        try {
            if (!StringUtils.isEmpty(userExtra.getXp())) {
                this.xp = JSONArray.parseArray(userExtra.getXp(), String.class);
            }
        } catch (Exception e) {
            this.xp = Arrays.asList(userExtra.getInterests().split(","));
        }
        try {
            if (!StringUtils.isEmpty(userExtra.getInterests())) {
                this.interests = JSONArray.parseArray(userExtra.getInterests(), String.class);
            }
        } catch (Exception e) {
            this.interests = Arrays.asList(userExtra.getInterests().split(","));
        }
        try {
            if (!StringUtils.isEmpty(userExtra.getTags())) {
                this.tags = JSONArray.parseArray(userExtra.getTags(), String.class);
            }
        } catch (Exception e) {
            this.interests = Arrays.asList(userExtra.getTags().split(","));
        }

    }

    private String profession;
    private String income;
    private String education;
    private List<String> xp;
    private String aim;
    private List<String> interests;
    private List<String> tags;
}
