package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import lombok.Data;

/**
 * @author tairanliu
 */
@Data
public class CorgiActivityDetail extends CorgiActivity {
    private Double match;

    public CorgiActivityDetail() {
        super();
    }

    public CorgiActivityDetail(CorgiActivity activity) {
        setActivityType(activity.getActivityType());
        setAddress(activity.getAddress());
        setBudget(activity.getBudget());
        setContent(activity.getContent());
        setEnlistTime(activity.getEnlistTime());
        setId(activity.getId());
        setLat(activity.getLat());
        setLng(activity.getLng());
        setPayType(activity.getPayType());
        setPeopleCount(activity.getPeopleCount());
        setPics(activity.getPics());
        setTitle(activity.getTitle());
        setUserId(activity.getUserId());
    }

    public CorgiActivityDetail initMatch(double match) {
        setMatch(match);
        return this;
    }
}
