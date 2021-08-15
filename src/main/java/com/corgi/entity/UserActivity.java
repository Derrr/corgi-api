package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import lombok.Data;

import java.text.ParseException;
import java.text.SimpleDateFormat;

@Data
public class UserActivity {
    public static final String LIKE = "like";
    public static final String COMMENT = "comment";
    public static final String CREATE = "create";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private String type;
    private Long optTime;
    private CorgiActivityDetail detail;

    public UserActivity(Long ctime) {
        this.optTime = ctime;
    }

    public UserActivity(String activityId, String ctime, String type) {
        this.type = type;
        this.detail = new CorgiActivityDetail();
        this.detail.setId(activityId);
        try {
            this.optTime = SDF.parse(ctime).getTime();
        } catch (ParseException e) {
        }
    }


    public boolean lesser(UserActivity userActivity) {
        if (userActivity == null || userActivity.getOptTime() == null) {
            return false;
        }
        if (optTime == null) {
            return true;
        }
        return optTime < userActivity.getOptTime();
    }
}
