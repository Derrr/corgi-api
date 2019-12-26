package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * @author tairanliu
 */
@Data
public class CorgiActivityDetail extends CorgiActivity {
    private Double match = 0.0;

    public CorgiActivityDetail() {
        super();
    }

    public CorgiActivityDetail(CorgiActivity activity) {
        BeanUtils.copyProperties(activity, this);
    }

    public CorgiActivityDetail initMatch(double match) {
        setMatch(match);
        return this;
    }
}
