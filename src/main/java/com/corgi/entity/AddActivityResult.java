package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.UserProfile;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class AddActivityResult extends CorgiActivity {
    private long activityCount;
    private List<CorgiActivityDetail> similarActivity;
    private List<UserProfile> recommendUser;
    private boolean canCallCity;
    private boolean hasCallCity;

    public static AddActivityResult getResult(CorgiActivity corgiActivity) {
        AddActivityResult result = new AddActivityResult();
        BeanUtils.copyProperties(corgiActivity, result);
        return result;
    }

    public AddActivityResult setRecommend(List<UserProfile> recommendUser) {
        this.recommendUser = recommendUser;
        return this;
    }

    public AddActivityResult setSimilar(List<CorgiActivityDetail> list) {
        this.similarActivity = list;
        return this;
    }

    public AddActivityResult setCount(long count) {
        this.activityCount = count;
        return this;
    }

    public AddActivityResult setCanCallCity(boolean canCallCity) {
        this.canCallCity = canCallCity;
        return this;
    }

    public AddActivityResult setHasCallCity(boolean hasCallCity) {
        this.hasCallCity = hasCallCity;
        return this;
    }
}
