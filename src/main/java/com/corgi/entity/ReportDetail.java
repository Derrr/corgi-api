package com.corgi.entity;

import com.corgi.user.entity.UserDetail;
import lombok.Data;

import java.util.List;

@Data
public class ReportDetail {
    private String id;
    private String reportUserId;
    private String reportUserName;
    private String accuseId;
    private String accuseType;
    private String accuseName;
    private String desc;
    private String reason;
    private String reportStatus;
    private String ctime;
    private String uptime;
    private Integer accuseTime;
    private String result;

    private List<String> pics;
    private UserDetail accuseUser;
}
