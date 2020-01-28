package com.corgi.service;

import com.corgi.common.CorgiQueueName;
import com.corgi.common.messages.PushMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author tairanliu
 */
@Service
public class PushService {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    public void sendMessage(PushMessage pushMessage){
        rabbitTemplate.convertAndSend(CorgiQueueName.PUSH_MESSAGE_QUEUE, pushMessage);
    }
}
