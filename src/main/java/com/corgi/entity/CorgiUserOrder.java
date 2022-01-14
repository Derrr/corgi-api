package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.CorgiMerchandise;
import lombok.Data;

@Data
public class CorgiUserOrder {
    private String id;
    private String tradeNo;
    private String merchId;
    private String seller;
    private String payType;
    private Double payAmount;
    private String orderId;
    private String status;
    private String desc;

    private String ctime;
    private CorgiMerchandise merchandise;
    private CorgiActivity activity;


    public String getCtime() {
        if (ctime != null) {
            ctime = ctime.substring(0, 19);
        }
        return ctime;
    }

}
