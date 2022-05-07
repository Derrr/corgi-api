package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.entity.ActivityQuery;
import com.corgi.user.api.*;
import com.corgi.user.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

@Slf4j
@Service
public class AsyncTaskService {
    @Reference
    private CorgiLikeService corgiLikeService;
    @Reference
    private CorgiCommentService corgiCommentService;
    @Reference
    private CorgiUserActivityService corgiUserActivityService;
    @Reference
    private CorgiUserService corgiUserService;

    @Async
    @Lazy
    public Future<List<ActivityLike>> getUserLike(Long timestamp, String userId, Integer size) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ActivityLike query = new ActivityLike();
        query.setLikeUserId(userId);
        query.setCtime(sdf.format(new Date()));
        if (timestamp > 0) {
            query.setCtime(sdf.format(new Date(timestamp)));
        }
        return new AsyncResult<>(corgiLikeService.queryLike(query, size));
    }

    @Async
    @Lazy
    public Future<List<ActivityComment>> getUserComment(Long timestamp, String userId, Integer size) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ActivityComment query = new ActivityComment();
        query.setCommentUserId(userId);
        query.setCtime(sdf.format(new Date()));
        if (timestamp > 0) {
            query.setCtime(sdf.format(new Date(timestamp)));
        }
        return new AsyncResult<>(corgiCommentService.queryComment(query, size));
    }

    @Async
    @Lazy
    public Future<List<CorgiActivity>> getUserActivity(Long timestamp, String userId, Integer size) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ActivityQuery query = new ActivityQuery();
        query.setUserId(userId);
        query.setEndTime(sdf.format(new Date()));
        if (timestamp > 0) {
            query.setEndTime(sdf.format(new Date(timestamp)));
        }
        query.setPageSize(size);
        return new AsyncResult<>(corgiUserActivityService.queryActivity(query));
    }

    @Async
    @Lazy
    public void initRecommendUser(String userId) {
        corgiUserService.initRecommendUserByUserId(userId);
    }

}
