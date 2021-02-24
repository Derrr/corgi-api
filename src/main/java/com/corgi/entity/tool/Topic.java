package com.corgi.entity.tool;

import com.corgi.entity.CorgiTopic;
import com.corgi.user.entity.CorgiVlog;

public class Topic extends CorgiTopic {

    private Integer likeCount;
    private Integer viewCount;
    private Integer commentCount;
    private Integer vlogCount;

    public void initCount(CorgiVlog vlog) {
        this.likeCount = vlog.getLikeCount();
        this.viewCount = vlog.getViewCount();
        this.commentCount = vlog.getCommentCount();
        this.vlogCount = vlog.getId();
    }
}
