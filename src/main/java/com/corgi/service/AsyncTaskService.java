package com.corgi.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.activity.entity.CorgiActivity;
import com.corgi.entity.ActivityQuery;
import com.corgi.user.api.CorgiCommentService;
import com.corgi.user.api.CorgiLikeService;
import com.corgi.user.api.CorgiUserActivityService;
import com.corgi.user.entity.ActivityComment;
import com.corgi.user.entity.ActivityLike;
import lombok.extern.slf4j.Slf4j;
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

    @Async
    public Future<List<ActivityLike>> getUserLike(Long timestamp, String userId, Integer size) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ActivityLike query = new ActivityLike();
        query.setLikeUserId(userId);
        query.setCtime(sdf.format(new Date()));
        if(timestamp > 0) {
            query.setCtime(sdf.format(new Date(timestamp)));
        }
        return new AsyncResult<>(corgiLikeService.queryLike(query, size));
    }

    @Async
    public Future<List<ActivityComment>> getUserComment(Long timestamp, String userId, Integer size) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ActivityComment query = new ActivityComment();
        query.setCommentUserId(userId);
        query.setCtime(sdf.format(new Date()));
        if(timestamp > 0) {
            query.setCtime(sdf.format(new Date(timestamp)));
        }
        return new AsyncResult<>(corgiCommentService.queryComment(query, size));
    }

    @Async
    public Future<List<CorgiActivity>> getUserActivity(Long timestamp, String userId, Integer size) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ActivityQuery query = new ActivityQuery();
        query.setUserId(userId);
        query.setEndTime(sdf.format(new Date()));
        if(timestamp > 0) {
            query.setEndTime(sdf.format(new Date(timestamp)));
        }
        query.setPageSize(size);
        return new AsyncResult<>(corgiUserActivityService.queryActivity(query));
    }
}
