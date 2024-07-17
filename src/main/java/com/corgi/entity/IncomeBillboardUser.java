package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.user.entity.UserDetail;
import lombok.Data;
import org.springframework.beans.BeanUtils;

/**
 * @author tairanliu
 */
@Data
public class IncomeBillboardUser {
    private String userId;
    private String avatar;
    private String nickname;
    private Integer age;
    private Integer height;
    private Integer weight;
    private Double price;
}
