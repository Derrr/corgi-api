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
public class ActivityBillboard extends CorgiActivity {

    private List<UserProfile> signUpUsers;
    private Integer signUpCount;

    public static ActivityBillboard getResult(CorgiActivity corgiActivity) {
        ActivityBillboard result = new ActivityBillboard();
        BeanUtils.copyProperties(corgiActivity, result);
        return result;
    }

}
