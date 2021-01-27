package com.corgi.service;

import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.PushMessage;
import com.corgi.common.messages.TraceFollow;
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
}
