package com.corgi.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeUtil {
    public static String buildTimeText(String ctime, Long now, SimpleDateFormat sdf) {
        try {
            Date date = sdf.parse(ctime.substring(0, 19));
            Long diff = now - date.getTime();
            if (diff < 30 * 60 * 1000) {
                return "刚刚";
            }
            if (diff < 60 * 60 * 1000) {
                return diff / (60 * 1000) + "分钟前";
            }
            if (diff < 24 * 3600 * 1000) {
                return diff / (3600 * 1000) + "小时前";
            }
            if (diff < 3 * 24 * 3600 * 1000) {
                return diff / (24 * 3600 * 1000) + "天前";
            }
            if (diff < 7 * 24 * 3600 * 1000) {
                return "一周内";
            }
            if (diff < 2 * 7 * 24 * 3600 * 1000) {
                return "两周内";
            }
            if (diff < 30 * 24 * 3600 * 1000) {
                return "本月内";
            }
            return "一个月以前";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
