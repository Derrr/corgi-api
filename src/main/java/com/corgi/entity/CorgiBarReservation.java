package com.corgi.entity;

import com.corgi.user.entity.BarProfile;
import com.corgi.user.entity.BarReservation;
import lombok.Data;


@Data
public class CorgiBarReservation extends BarReservation {
    private BarProfile barProfile;
    private Double payAmount;
    private String payTime;
    private String orderNo;
}
