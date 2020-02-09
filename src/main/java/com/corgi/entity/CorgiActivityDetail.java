package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserPic;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class CorgiActivityDetail extends CorgiActivity {
    private List<CorgiActivityDetail> similarActivity;
    private UserDetail userDetail;
    private Integer signUpStatus;
    private Double match = 0.0;
    private Integer height = 0;
    private Integer width = 0;

    public CorgiActivityDetail() {
        super();
    }

    public CorgiActivityDetail(CorgiActivity activity) {
        BeanUtils.copyProperties(activity, this);
    }

    public CorgiActivityDetail initSignUpStatus(Integer signUpStatus) {
        if (signUpStatus == null) {
            signUpStatus = 0;
        }
        this.signUpStatus = signUpStatus;
        return this;
    }

    public CorgiActivityDetail initUserDetail(UserDetail userDetail) {
        this.userDetail = userDetail;
        return this;
    }

    public CorgiActivityDetail initMatch(double match) {
        setMatch(match);
        return this;
    }

    public CorgiActivityDetail initSize(int height, int width) {
        this.height = height;
        this.width = width;
        return this;
    }

    public CorgiActivityDetail initSimilarActivity(List<CorgiActivityDetail> similarActivity) {
        this.similarActivity = similarActivity;
        return this;
    }
}
