package com.platform.mapper.mq;

import com.belle.topsports.new_epp_java_sdk.mq.MQLog;

public interface RabbitMQMapper {
    void insertMQLog(MQLog log);
}
