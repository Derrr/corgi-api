package com.corgi.entity;

import com.corgi.activity.entity.CorgiActivity;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

@Data
public class AddActivityResult extends CorgiActivity {
    private List<CorgiActivity> similarActivity;

    public static AddActivityResult getResult(CorgiActivity corgiActivity) {
        AddActivityResult result = new AddActivityResult();
        BeanUtils.copyProperties(corgiActivity, result);
        return result;
    }

    public AddActivityResult setSimilar(List<CorgiActivity> list) {
        this.similarActivity = list;
        return this;
    }
}
