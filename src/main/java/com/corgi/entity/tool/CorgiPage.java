package com.corgi.entity.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author tairanliu
 */
@Data
@Builder()
public class CorgiPage<T> {
    private int page;
    private int pageSize;
    private long count;
    private List<T> result;

}
