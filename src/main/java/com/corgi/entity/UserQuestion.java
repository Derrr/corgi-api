package com.corgi.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;

/**
 * @author tairanliu
 */
@Data
public class UserQuestion implements Serializable {
    private String question;
    private HashMap options;

    public UserQuestion setQuestion(String question) {
        this.question = question;
        return this;
    }

    public UserQuestion addOption(String key, String value) {
        if (options == null) {
            options = new HashMap();
        }
        options.put(key, value);
        return this;
    }
}
