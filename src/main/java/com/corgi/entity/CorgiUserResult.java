package com.corgi.entity;

import com.corgi.user.entity.UserEvaluation;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class CorgiUserResult implements Serializable {
    private String result;
    private Integer total;
}
