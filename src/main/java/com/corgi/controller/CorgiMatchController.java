package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.CorgiConstants;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.entity.Matcher;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.*;
import com.corgi.user.entity.UserMatchRemain;
import com.corgi.user.entity.UserQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("match")
public class CorgiMatchController extends BaseController {
    @Reference
    private CorgiUserMatchService corgiUserMatchService;
    @Autowired
    private CorgiUtilService corgiUtilService;

    @PostMapping("get_matches")
    public JsonResult addActivity(@RequestBody UserQuery userQuery) {
        if (hasUserId()) {
            userQuery.setUserId(getUserId());
        }
        String key = "matching-" + userQuery.getUserId();
        try {
            if (corgiUtilService.lock(key)) {
                return new JsonResult(corgiUserMatchService.getUserMatchItem(userQuery));
            }
        } finally {
            corgiUtilService.unlock(key);
        }
        return new JsonResult();
    }

    @PostMapping("desire")
    public JsonResult addActivity(@RequestBody Matcher matcher) {
        List<String> matchIds = matcher.getMatchIds();
        if (matchIds == null) {
            matchIds = new ArrayList<>();
        }
        String key = "count_matching-" + getUserId();
        Integer result = 0;
        try {
            if (corgiUtilService.lock(key)) {
                List<UserMatchRemain> remains = corgiUserMatchService.countUserRemain(getUserId());
                Integer remain = 0;
                if (!CollectionUtils.isEmpty(remains)) {
                    for (UserMatchRemain remain1 : remains) {
                        remain += remain1.getRemain();
                    }
                }
                result = remain - matchIds.size();
                if (result < 0) {
                    return new JsonResult(Constants.API_ERROR_CODE, "用户速配次数不足");
                }
                if (!CollectionUtils.isEmpty(remains) && !CollectionUtils.isEmpty(matchIds)) {
                    int i = 0;
                    for (UserMatchRemain remain1 : remains) {
                        Integer size = remain1.getRemain();
                        for (int j = size; j > 0; j--) {
                            if (i >= matchIds.size()) {
                                return new JsonResult(result);
                            }
                            String matchId = matchIds.get(i);
                            corgiUserMatchService.addUserMatch(getUserId(), matchId, remain1.getTradeNo());
                            i++;
                        }
                    }
                }
            }
        } finally {
            corgiUtilService.unlock(key);
        }
        return new JsonResult(result);
    }

}
