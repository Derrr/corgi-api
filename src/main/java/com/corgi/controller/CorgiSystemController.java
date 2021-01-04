package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.api.CorgiActivityService;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.common.JsonResult;
import com.corgi.user.api.CorgiBarService;
import com.corgi.user.api.CorgiSystemMessageService;
import com.corgi.user.entity.BarProfile;
import com.corgi.user.entity.MessageRecord;
import com.corgi.user.entity.MessageRule;
import com.corgi.user.entity.SystemMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("system")
public class CorgiSystemController extends BaseController {
    @Reference
    private CorgiSystemMessageService corgiSystemMessageService;

    @PostMapping("add_system_message")
    public JsonResult addSystemMessage(@RequestBody SystemMessage systemMessage) {
        corgiSystemMessageService.addSystemMessage(systemMessage);
        return new JsonResult();
    }

    @PostMapping("update_system_message")
    public JsonResult updateSystemMessage(@RequestBody SystemMessage systemMessage) {
        log.info("sent time...{} ", systemMessage.getSentTime());
        corgiSystemMessageService.updateSystemMessage(systemMessage);
        return new JsonResult();
    }

    @GetMapping("get_system_message")
    public JsonResult getSystemMessage(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<SystemMessage> systemMessages = corgiSystemMessageService.getSystemMessageByPage(page, pageSize);
        return new JsonResult(systemMessages);
    }

    @GetMapping("get_message_record")
    public JsonResult getMessageRecord(@RequestParam(required = false, name = "messageId") String messageId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<MessageRecord> messageRecords;
        if (StringUtils.isEmpty(messageId)) {
            messageRecords = corgiSystemMessageService.getMessageRecordByPage(page, pageSize);
        } else {
            messageRecords = corgiSystemMessageService.getMessageRecordByMessageId(page, pageSize, messageId);
        }
        return new JsonResult(messageRecords);
    }


}

