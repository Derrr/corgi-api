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
    private Long operateTime;
    private CorgiActivityDetail detail;

    public UserActivity(Long ctime) {
        this.operateTime = ctime;
    }

    public UserActivity(String activityId, String ctime, String type) {
        this.type = type;
        this.detail = new CorgiActivityDetail();
        this.detail.setId(activityId);
        try {
            this.operateTime = SDF.parse(ctime).getTime();
        } catch (ParseException e) {
        }
    }


    public boolean lesser(UserActivity userActivity) {
        if (userActivity == null || userActivity.getOperateTime() == null) {
            return false;
        }
        if (operateTime == null) {
            return true;
        }
        return operateTime < userActivity.getOperateTime();
    }
}
