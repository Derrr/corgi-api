package com.corgi.entity;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author tairanliu
 */
@Data
@Builder
public class MailMessage {
    private String userId;
    private String content;
    private String telNo;
    private String customize;
    private List<String> pics;
}
