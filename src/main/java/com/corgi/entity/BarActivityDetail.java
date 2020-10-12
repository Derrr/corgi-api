package com.corgi.entity;

import com.alibaba.dubbo.common.utils.CollectionUtils;
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
public class BarActivityDetail extends CorgiActivity {
    private BarProfile barDetail;

    private List<CorgiCoupon> coupons;

    public BarActivityDetail() {
        super();
    }

    public BarActivityDetail(CorgiActivity activity) {
        BeanUtils.copyProperties(activity, this);
    }
}
