package com.corgi.entity;

import lombok.Data;

import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class MailMessage {
    private String userId;
    private String content;
    private String telNo;
    private List<String> pics;
}
