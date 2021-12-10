package com.corgi.common.util;

import org.springframework.util.DigestUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class UuidUtil {
    public static String getTradeNo(String userId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        String md5Id = DigestUtils.md5DigestAsHex(userId.getBytes());
        String high1 = md5Id.substring(0, 8);
        String high2 = md5Id.substring(8, 16);
        String low1 = md5Id.substring(16, 24);
        String low2 = md5Id.substring(24, 32);
        Long new1 = Long.parseLong(high1, 16) ^ Long.parseLong(high2, 16)
                ^ Long.parseLong(low1, 16) ^ Long.parseLong(low2, 16);
        String newMd5Id = (new1 % 10000000) + "";
        while (7 < newMd5Id.length()) {
            newMd5Id = "0" + newMd5Id;
        }
        return sdf.format(new Date()) + newMd5Id;
    }
}
