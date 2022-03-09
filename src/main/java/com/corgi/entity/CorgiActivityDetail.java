package com.corgi.entity;

import com.alibaba.dubbo.common.utils.CollectionUtils;
import com.corgi.activity.entity.ActivityPic;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.*;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class CorgiActivityDetail extends CorgiActivity {
    private UserDetail userDetail;
    private BarProfile barDetail;
    private Integer signUpStatus;
    private Double match = 0.0;
    private Long commentCount;
    private Long likeCount = 0L;
    private Integer hasLike = 0;
    private Integer hasSwiftComment = 0;
    private Integer shareCount = 0;
    private ActivityComment lastComment;
    private Integer signUpCount;
    private List<ActivityLike> likeUsers;
    private List<SignUpUser> signUpUsers;
    private List<UserDetail> buyers;
    private boolean hasCallCity;
    private boolean canCallCity;
    private Integer isFollowed;
    private String timeShow;
    private String picUrl;
    private String activityId;
    private CorgiMerchandise merchandise;

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

    public CorgiActivityDetail initCommentCount(Long commentCount) {
        this.commentCount = commentCount;
        return this;
    }

    public CorgiActivityDetail initLikeCount(Long likeCount) {
        this.likeCount = likeCount;
        super.setLikeCount(likeCount);
        return this;
    }

    public CorgiActivityDetail initLikeUsers(List<ActivityLike> likeUsers) {
        this.likeUsers = likeUsers;
        return this;
    }

    public CorgiActivityDetail hasLike(Integer hasLike) {
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

    public CorgiActivityDetail initSize(Long height, Long width) {
        this.setHeight(height);
        this.setWidth(width);
        return this;
    }

    public CorgiActivityDetail initSignUpUsers(List<UserProfile> userProfiles) {
        this.signUpUsers = new ArrayList<>();
        for (UserProfile userProfile : userProfiles) {
            signUpUsers.add(SignUpUser.builder()
                    .signUpUserId(userProfile.getUserId())
                    .signUpUserName(userProfile.getNickname())
                    .signUpUserAvatar(CollectionUtils.isEmpty(userProfile.getPics()) ? "" : userProfile.getPics().get(0).getPicUrl())
                    .build());
        }
        return this;
    }

    public String getPicUrl() {
        List<ActivityPic> picUrls = super.getPics();
        if (CollectionUtils.isNotEmpty(picUrls)) {
            return picUrls.get(0).getPicUrl();
        }
        return super.getCoverUrl();
    }

    public String getActivityId() {
        return super.getId();
    }
}
