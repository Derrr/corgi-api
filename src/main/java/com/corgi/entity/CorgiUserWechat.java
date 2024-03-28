package com.corgi.entity;

import com.alibaba.dubbo.common.utils.StringUtils;
import com.corgi.user.entity.CorgiMerchandise;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserWechat;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

@Data
public class CorgiUserWechat extends UserWechat {

    private String payStatus;
    private CorgiMerchandise merchandise;
    private Integer age;
    private Integer height;
    private Integer weight;

    public static CorgiUserWechat getWechat(UserWechat wechat) {
        CorgiUserWechat result = new CorgiUserWechat();
        BeanUtils.copyProperties(wechat, result);
        return result;
    }

    public void initUserDetail(UserDetail userDetail) {
        if (userDetail == null) {
            return;
        }
        if (userDetail.getHeight() > 0) {
            this.height = userDetail.getHeight();
        }
        if (userDetail.getWeight() > 0) {
            this.height = userDetail.getHeight();
        }
        try {
            if (StringUtils.isNotEmpty(userDetail.getBirthday())) {
                Calendar today = Calendar.getInstance();
                Calendar birthDay = Calendar.getInstance();
                birthDay.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(userDetail.getBirthday()));
                this.age = today.get(Calendar.YEAR) - birthDay.get(Calendar.YEAR);
                if (today.get(Calendar.MONTH) < birthDay.get(Calendar.MONTH) ||
                        (today.get(Calendar.MONTH) == birthDay.get(Calendar.MONTH) &&
                                today.get(Calendar.DAY_OF_MONTH) < birthDay.get(Calendar.DAY_OF_MONTH))) {
                    this.age--;
                }
            }
        } catch (Exception e) {

        }
    }
}
