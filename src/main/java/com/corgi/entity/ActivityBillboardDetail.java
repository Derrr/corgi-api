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
public class ActivityBillboardDetail extends CorgiActivity {

    private List<UserProfile> signUpUsers;
    private Integer signUpCount;
    private String lastTime;

    public static ActivityBillboardDetail getResult(CorgiActivity corgiActivity) {
        ActivityBillboardDetail result = new ActivityBillboardDetail();
        BeanUtils.copyProperties(corgiActivity, result);
        return result;
    }

}
