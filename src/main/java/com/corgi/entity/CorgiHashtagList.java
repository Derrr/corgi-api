package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.entity.tool.Hashtag;
import lombok.Data;

import java.util.List;

@Data
public class CorgiHashtagList {

    private Hashtag hashtagDetail;
    private List<CorgiActivity> activityList;

    public CorgiHashtagList(Hashtag hashtagDetail, List<CorgiActivity> activityList) {
        this.hashtagDetail = hashtagDetail;
        this.activityList = activityList;
    }
}
