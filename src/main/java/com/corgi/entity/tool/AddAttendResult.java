package com.corgi.entity.tool;

import com.corgi.activity.entity.CorgiActivity;
import com.corgi.entity.CorgiActivityDetail;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * @author tairanliu
 */
@Data
public class AddAttendResult extends CorgiActivity {
    private Integer attendResult;

    public static AddAttendResult getResult(CorgiActivity corgiActivity, Integer addResult) {
        AddAttendResult result = new AddAttendResult();
        BeanUtils.copyProperties(corgiActivity, result);
        result.setAttendResult(addResult);
        return result;
    }
}
