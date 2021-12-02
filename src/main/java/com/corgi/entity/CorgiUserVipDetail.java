package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import lombok.Data;

/**
 * @author tairanliu
 */
@Data
public class CorgiUserVipDetail extends CorgiActivity {
    private String expireDate = "-";
    private Integer remainDate = 0;
}
