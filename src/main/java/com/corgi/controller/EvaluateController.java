package com.corgi.controller;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.entity.CorgiUserEvaluation;
import com.corgi.service.AliyunGreenService;
import com.corgi.service.AliyunNLPService;
import com.corgi.service.CorgiUtilService;
import com.corgi.user.api.CorgiEvaluationService;
import com.corgi.user.api.CorgiUserDateService;
import com.corgi.user.api.CorgiUserFollowService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.CorgiDateApply;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserEvaluation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    @Autowired
    private AliyunNLPService aliyunNLPService;
    @Autowired
    private AliyunGreenService aliyunGreenService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("add_evaluation")
    public JsonResult addEvaluation(@RequestBody UserEvaluation userEvaluation) {
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
                if (!CorgiDateApply.AGREE.equals(apply.getStatus())) {
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
                this.addUserEvaluation(userEvaluation);
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
//        if (!corgiUtilService.tryLock(friendKey, System.currentTimeMillis() + "", 23L, TimeUnit.HOURS)) {
//            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "一天只能评价一次哦");
//        }
        String evaluationId = this.addUserEvaluation(userEvaluation);
        redisTemplate.opsForValue().set(friendKey, evaluationId, 23, TimeUnit.HOURS);
        return new JsonResult();
    }

    private String addUserEvaluation(UserEvaluation userEvaluation) {
        if (aliyunGreenService.checkText(userEvaluation.getTag())) {
            userEvaluation.setCheckStatus(AliyunGreenService.PASS);
        } else {
            userEvaluation.setCheckStatus(AliyunGreenService.CHECK);
        }
        Double score = aliyunNLPService.getSaChe(userEvaluation.getTag());
        if (score == null) {
            score = corgiEvaluationService.getTagScore(userEvaluation.getTag());
        }
        userEvaluation.setScore(score);
        return corgiEvaluationService.addEvaluation(userEvaluation);
    }

    @GetMapping("can_evaluate")
    public JsonResult canEvaluate(@RequestParam("userId") String userId) {
        int match = corgiUserFollowService.isFollowed(getUserId(), userId);
        if (match < 3) {
            return new JsonResult("0");
        }
        String friendKey = "friend_evaluate_" + getUserId() + "-" + userId;
        String id = redisTemplate.opsForValue().get(friendKey);
        if (!StringUtils.isEmpty(id)) {
            return new JsonResult("0");
        }
//        if (!corgiUtilService.tryLock(friendKey, System.currentTimeMillis() + "", 20L, TimeUnit.HOURS)) {
//            return new JsonResult("0");
//        }
        return new JsonResult("1");
    }

    @GetMapping("get_user_evaluation")
    public JsonResult getUserEvaluation(@RequestParam("userId") String userId, @RequestParam("pageSize") Integer pageSize) {
        List<UserEvaluation> tags = corgiEvaluationService.getEvaluationByHeat(userId, getUserId(), pageSize);
        Double totalScore = corgiEvaluationService.getUserEvaluation(userId);
        Integer userCount = corgiEvaluationService.getUserCount(userId);
        CorgiUserEvaluation evaluation = new CorgiUserEvaluation();
        evaluation.setTags(tags);
        evaluation.setTotalScore(totalScore);
        evaluation.setUserCount(userCount);
        return new JsonResult(evaluation);
    }

    @GetMapping("get_recent_evaluation")
    public JsonResult getRecentEvaluation(@RequestParam("userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        return new JsonResult(corgiEvaluationService.getEvaluationByUser(userId, getUserId(), page, pageSize));
    }

    @GetMapping("get_my_evaluation")
    public JsonResult getMyEvaluation(@RequestParam(required = false, name = "userId") String userId, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        if (StringUtils.isEmpty(userId)) {
            userId = getUserId();
        }
        return new JsonResult(corgiEvaluationService.getEvaluationByEvaluator(userId, page, pageSize));
    }

    @GetMapping("get_tag_evaluation")
    public JsonResult getTagEvaluation(@RequestParam("userId") String userId, @RequestParam("tag") String tag, @RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        return new JsonResult(corgiEvaluationService.getEvaluationByTag(userId, tag, page, pageSize));
    }

    @GetMapping("delete_evaluation")
    public JsonResult deleteEvaluation(@RequestParam("id") Integer id) {
        UserEvaluation oldUserEvaluation = corgiEvaluationService.getEvaluationById(id + "");
        UserEvaluation userEvaluation = new UserEvaluation();
        userEvaluation.setId(id);
        if (hasUserId()) {
            userEvaluation.setEvaluatorId(getUserId());
        }
        corgiEvaluationService.deleteEvaluation(userEvaluation);
        if (oldUserEvaluation != null) {
            String friendKey = "friend_evaluate_" + getUserId() + "-" + oldUserEvaluation.getUserId();
            redisTemplate.delete(friendKey);
        }
        return new JsonResult();
    }

    @GetMapping("delete_evaluation_by_tag")
    public JsonResult deleteEvaluationByTag(UserEvaluation userEvaluation) {
        if (hasUserId()) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "删除tag请联系管理员");
        }
        userEvaluation.setEvaluatorId("");
        corgiEvaluationService.deleteEvaluation(userEvaluation);
        return new JsonResult();
    }

    @GetMapping("get_date_evaluation")
    public JsonResult getDateEvaluation(@RequestParam("applyId") String applyId, @RequestParam(required = false, name = "evaluatorId") String evaluatorId) {
        return new JsonResult(corgiEvaluationService.getDateEvaluation(applyId, evaluatorId));
    }

    @GetMapping("get_need_evaluation")
    public JsonResult getNeedEvaluation(@RequestParam("page") Integer page, @RequestParam("pageSize") Integer pageSize) {
        List<CorgiDateApply> applies = corgiEvaluationService.getNeeEvaluation(getUserId(), page, pageSize);
        String userId = getUserId();
        for (CorgiDateApply apply : applies) {
            if (userId.equals(apply.getApplyUserId())) {
                apply.setUserInfo(corgiUserService.getUserDetailBasic(apply.getApprovalUserId()));
            } else {
                apply.setUserInfo(corgiUserService.getUserDetailBasic(apply.getApplyUserId()));
            }
            apply.setResult("我们完成了一次约会，帮我评价吧！");
        }
        return new JsonResult(applies);
    }

    @GetMapping("like")
    public JsonResult like(@RequestParam("evaluationId") String evaluationId) {
        UserEvaluation userEvaluation = corgiEvaluationService.getEvaluationById(evaluationId);
        if (userEvaluation == null) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "评论不存在");
        }
        String friendKey = "friend_evaluate_" + getUserId() + "-" + userEvaluation.getUserId();
        String oldEvaluationId = redisTemplate.opsForValue().get(friendKey);
        if (oldEvaluationId != null) {
            UserEvaluation oldEvaluation = corgiEvaluationService.getEvaluationById(oldEvaluationId);
            if (oldEvaluation != null && oldEvaluation.getEvaluatorId().equals(getUserId())) {
                if (userEvaluation.getTag().equals(oldEvaluation.getTag())) {
                    corgiEvaluationService.deleteEvaluation(oldEvaluation);
                    redisTemplate.delete(friendKey);
                    return new JsonResult(corgiEvaluationService.countByTag(userEvaluation.getTag(), userEvaluation.getUserId()));
                }
            }
            //return new JsonResult(Constants.PARAMETER_ERROR_CODE, "一天只能评价一次哦");
        }
        int match = corgiUserFollowService.isFollowed(getUserId(), userEvaluation.getUserId());
        if (match < 3) {
            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "只有匹配好友可以评价哦");
        }
//        if (!corgiUtilService.tryLock(friendKey, System.currentTimeMillis() + "", 23L, TimeUnit.HOURS)) {
//            return new JsonResult(Constants.PARAMETER_ERROR_CODE, "一天只能评价一次哦");
//        }
        UserDetail userDetail = corgiUserService.getUserDetailBasic(getUserId());
        userEvaluation.setEvaluatorId(userDetail.getUserId());
        userEvaluation.setEvaluatorName(userDetail.getNickname());
        userEvaluation.setEvaluatorAvatar(userDetail.getAvatar());
        evaluationId = corgiEvaluationService.addEvaluation(userEvaluation);
        redisTemplate.opsForValue().set(friendKey, evaluationId, 23, TimeUnit.HOURS);
        return new JsonResult(corgiEvaluationService.countByTag(userEvaluation.getTag(), userEvaluation.getUserId()));
    }

    @GetMapping("unlike")
    public JsonResult unlike(@RequestParam("evaluationId") String evaluationId) {
        corgiEvaluationService.unlikeEvaluation(getUserId(), evaluationId);
        return new JsonResult();
    }
}
