package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
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

    public static AddActivityResult getResult(CorgiActivity corgiActivity) {
        AddActivityResult result = new AddActivityResult();
        BeanUtils.copyProperties(corgiActivity, result);
        return result;
    }

    public AddActivityResult setSimilar(List<CorgiActivityDetail> list) {
        this.similarActivity = list;
        return this;
    }

    public AddActivityResult setCount(long count) {
        this.activityCount = count;
        return this;
    }
}
