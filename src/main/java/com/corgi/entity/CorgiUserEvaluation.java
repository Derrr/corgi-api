package com.corgi.entity;

import com.corgi.user.entity.UserEvaluation;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class CorgiUserEvaluation implements Serializable {
    private Double totalScore;
    private Integer userCount;
    private List<UserEvaluation> tags;
}
