package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.ActivityComment;
import com.corgi.user.entity.ActivityLike;
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
    private Long commentCount;
    private Long likeCount;
    private Integer hasLike = 0;
    private ActivityComment lastComment;
    private Integer signUpCount;
    private List<ActivityLike> likeUsers;

    public CorgiActivityDetail() {
        super();
    }

    public CorgiActivityDetail(CorgiActivity activity) {
        BeanUtils.copyProperties(activity, this);
    }

    public CorgiActivityDetail initSignUpStatus(Integer signUpStatus) {
        if (signUpStatus == null) {
            signUpStatus = -1;
        }
        this.signUpStatus = signUpStatus;
        return this;
    }

    public CorgiActivityDetail initCommentCount(Long commentCount){
        this.commentCount = commentCount;
        return this;
    }

    public CorgiActivityDetail initLikeCount(Long likeCount){
        this.likeCount = likeCount;
        return this;
    }

    public CorgiActivityDetail initLikeUsers(List<ActivityLike> likeUsers){
        this.likeUsers = likeUsers;
        return this;
    }

    public CorgiActivityDetail hasLike(Integer hasLike){
        this.hasLike = hasLike;
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
