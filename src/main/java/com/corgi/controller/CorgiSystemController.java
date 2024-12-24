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

    @GetMapping("search_system_message")
    public JsonResult searchSystemMessage(MessageRecord record, @RequestParam("page") Integer page, @RequestParam("size") Integer size) {
        return new JsonResult(corgiSystemMessageService.searchMessageRecord(record, page, size));
    }


    @PostMapping("update_system_message")
    public JsonResult updateSystemMessage(@RequestBody SystemMessage systemMessage) {
        log.info("sent time...{} ", systemMessage.getSentTime());
        corgiSystemMessageService.updateSystemMessage(systemMessage);
        return new JsonResult();
    }

    @GetMapping("get_system_message")
    public JsonResult getSystemMessage(
            @RequestParam(value = "filterTitle", required = false) String filterTitle,
            @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<SystemMessage> systemMessages = corgiSystemMessageService.getSystemMessageByPage(filterTitle, page, pageSize);
        JsonResult result = new JsonResult(systemMessages);
        result.setTotal(corgiSystemMessageService.countSystemMessage(filterTitle));
        return result;
    }

    @GetMapping("get_message_record")
    public JsonResult getMessageRecord(@RequestParam(required = false, name = "messageId") String messageId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<MessageRecord> messageRecords;
        Integer total = 0;
        if (StringUtils.isEmpty(messageId)) {
            messageRecords = corgiSystemMessageService.getMessageRecordByPage(page, pageSize);
        } else {
            messageRecords = corgiSystemMessageService.getMessageRecordByMessageId(page, pageSize, messageId);
            total = corgiSystemMessageService.countMessageRecordByMessageId(messageId);
        }
        JsonResult result = new JsonResult(messageRecords);
        result.setTotal(total);
        return result;
    }


}

