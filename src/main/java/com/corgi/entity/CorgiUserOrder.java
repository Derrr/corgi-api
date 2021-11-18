package com.corgi.entity;

import com.corgi.user.entity.CorgiMerchandise;
import lombok.Data;

@Data
public class CorgiUserOrder {
    private String id;
    private String tradeNo;
    private String merchId;
    private String seller;
    private String payType;
    private String status;
    private String ctime;
    private CorgiMerchandise merchandise;
}
