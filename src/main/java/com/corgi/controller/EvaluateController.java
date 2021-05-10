package com.corgi.controller;

import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.CorgiEvaluationService;
import com.corgi.user.api.CorgiUserDateService;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.CorgiDateApply;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserEvaluation;
import jdk.nashorn.internal.ir.annotations.Reference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author tairanliu
 */
@Slf4j
@RestController
@RequestMapping("evaluate")
public class EvaluateController extends BaseController {

    @Reference
    private CorgiEvaluationService corgiEvaluationService;
    @Reference
    private CorgiUserDateService corgiUserDateService;
    @Reference
    private CorgiUserService corgiUserService;
    @Reference
    private CorgiUserFollowService corgiUserFollowService;
    @Autowired
    private CorgiUtilService corgiUtilService;

    @PostMapping("add_evaluation")
    public JsonResult addEvaluation(@RequestBody UserEvaluation userEvaluation) {
        log.info(corgiUserService + "" + getUserId());
        UserDetail detail = corgiUserService.getUserDetailBasic(getUserId());
        userEvaluation.setEvaluatorId(detail.getUserId());
        userEvaluation.setEvaluatorName(detail.getNickname());
        userEvaluation.setEvaluatorAvatar(detail.getAvatar());

        UserDetail userDetail = corgiUserService.getUserDetailBasic(userEvaluation.getUserId());
        userEvaluation.setUserId(userDetail.getUserId());
        userEvaluation.setUserName(userDetail.getNickname());
        userEvaluation.setUserAvatar(userDetail.getAvatar());

        if (UserEvaluation.TYPE_DATE.equals(userEvaluation.getType())) {
            String applyId = userEvaluation.getApplyId();
            try {
                CorgiDateApply apply = corgiUserDateService.getApplyDetail(Integer.valueOf(applyId));
                if (apply == null) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "约会不存在");
                }
                if (CorgiDateApply.CANCEL.equals(apply.getStatus())) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "约会已取消");
                }
                if (!CorgiDateApply.APPLY.equals(apply.getStatus())) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "还不能评价该约会");
                }
                if (!getUserId().equals(apply.getApplyUserId()) && !getUserId().equals(apply.getApprovalUserId())) {
                    return new JsonResult(Constants.PARAMETER_ERROR_CODE, "未参与该约会");
                }
            } catch (Exception e) {
                return new JsonResult(Constants.PARAMETER_ERROR_CODE, "约会不存在");
            }
            String dateKey = "date_evaluate_" + applyId + "-" + getUserId();
            try {
                if (!corgiUtilService.lock(dateKey)) {
                    return new JsonResult(Constants.API_ERROR_CODE, "添加评论失败");
                }
                List<UserEvaluation> evaluationList = corgiEvaluationService.getDateEvaluation(userEvaluation.getApplyId(), getUserId());
                if (!CollectionUtils.isEmpty(evaluationList)) {
                    return new JsonResult(Constants.API_ERROR_CODE, "已评价过该约会");
                }
                corgiEvaluationService.addEvaluation(userEvaluation);
                return new JsonResult();
            } finally {
                corgiUtilService.unlock(dateKey);
            }
        }
        userEvaluation.setType(UserEvaluation.TYPE_FRIEND);
        int match = corgiUserFollowService.isFollowed(getUserId(), userEvaluation.getUserId());
        if (match < 3) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "只有匹配好友可以评价哦");
        }
        String friendKey = "friend_evaluate_" + getUserId() + "-" + userEvaluation.getUserId();
        if (!corgiUtilService.tryLock(friendKey, System.currentTimeMillis() + "", 7L, TimeUnit.DAYS)) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "评价过于频繁");
        }
        corgiEvaluationService.addEvaluation(userEvaluation);
        return new JsonResult();
    }

    @GetMapping("get_user_evaluation")
    public JsonResult getUserEvaluation(@RequestParam("userId") String userId) {
        return new JsonResult(corgiEvaluationService.getEvaluationByUser(userId));
    }

    @GetMapping("get_my_evaluation")
    public JsonResult getMyEvaluation(@RequestParam(required = false, name = "userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiEvaluationService.getEvaluationByEvaluator(userId, page, pageSize));
    }

    @GetMapping("delete_evaluation")
    public JsonResult deleteEvaluation(@RequestParam("id") Integer id) {
        UserEvaluation userEvaluation = new UserEvaluation();
        userEvaluation.setId(id);
        userEvaluation.setEvaluatorId(getUserId());
        corgiEvaluationService.deleteEvaluation(userEvaluation);
        return new JsonResult();
    }

    @GetMapping("get_date_evaluation")
    public JsonResult getDateEvaluation(@RequestParam("applyId") String applyId, @RequestParam(required = false, name = "evaluatorId") String evaluatorId) {
        return new JsonResult(corgiEvaluationService.getDateEvaluation(applyId, evaluatorId));
    }
}
