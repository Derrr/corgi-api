package com.corgi.entity;

import lombok.Data;

@Data
public class CheckTextResult {
    boolean pass = true;
    String content = "";
    String originContent = "";
}
