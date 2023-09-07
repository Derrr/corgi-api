package com.corgi.entity;

import lombok.Data;

import java.util.List;

@Data
public class Matcher {
    private List<String> matchIds;
    private String type;
    private String greeting;
    private List<String> filterSource;
}
