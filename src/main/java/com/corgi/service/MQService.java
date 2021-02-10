package com.corgi.service;

import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
import com.corgi.user.entity.CorgiDate;
import com.corgi.user.entity.UserTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author tairanliu
 */
@Slf4j
@Service
public class MQService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(PushMessage pushMessage) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.PUSH_MESSAGE_QUEUE, pushMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void sendBarActivityMessage(PushMessage pushMessage) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.BAR_ACTIVITY_QUEUE, pushMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void sendInfluencerMessage(PushMessage pushMessage) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.INFLUENCER_JOIN_QUEUE, pushMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void sendInfluencerLeftMessage(PushMessage pushMessage) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.INFLUENCER_LEFT_QUEUE, pushMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void refreshFeed(String userId) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.FEED_REFRESH, userId);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void sendRegisterMessage(PushMessage pushMessage) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.REGISTER_QUEUE, pushMessage);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void sendTrace(TraceFollow traceFollow) {
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.TRACE_FOLLOW_QUEUE, traceFollow);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void sendDate(CorgiDate corgiDate){
        try {
            rabbitTemplate.convertAndSend(CorgiQueueName.USER_DATE_QUEUE, corgiDate);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
