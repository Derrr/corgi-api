package com.corgi.entity;

import com.corgi.user.entity.CorgiMerchandise;
import com.corgi.user.entity.UserWechat;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class CorgiUserWechat extends UserWechat {

    private String payStatus;
    private CorgiMerchandise merchandise;

    public static CorgiUserWechat getWechat(UserWechat wechat){
        CorgiUserWechat result = new CorgiUserWechat();
        BeanUtils.copyProperties(wechat,result);
        return result;
    }
}
