package com.corgi.entity;

import com.alibaba.fastjson.JSONArray;
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
        if (!StringUtils.isEmpty(userExtra.getXp())) {
            this.xp = Arrays.asList(userExtra.getXp().split(","));
        }
        if (!StringUtils.isEmpty(userExtra.getInterests())) {
            this.interests = Arrays.asList(userExtra.getInterests().split(","));
        }
        if (!StringUtils.isEmpty(userExtra.getTags())) {
            this.tags = Arrays.asList(userExtra.getTags().split(","));
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
