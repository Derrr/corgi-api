package com.corgi.entity;

import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.CorgiVlog;
import com.corgi.user.entity.UserDetail;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class VlogDetail extends CorgiVlog {
    public static VlogDetail createDetail(CorgiVlog corgiVlog) {
        VlogDetail detail = new VlogDetail();
        BeanUtils.copyProperties(corgiVlog, detail);
        return detail;
    }

    private Integer expectView;
    private UserDetail userDetail;
    private CorgiActivity activityDetail;
    private Integer hasLike;
    private Integer shareCount;
}
