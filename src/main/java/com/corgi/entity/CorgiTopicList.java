package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import lombok.Data;

import java.util.List;

@Data
public class CorgiTopicList {

    private CorgiTopic topicDetail;
    private List<CorgiActivity> activityList;

    public CorgiTopicList(CorgiTopic topic, List<CorgiActivity> activityList) {
        this.topicDetail = topic;
        this.activityList = activityList;
    }
}
