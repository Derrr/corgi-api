package com.corgi.entity.tlx;

import com.corgi.user.entity.UserDetail;
import lombok.Data;

import java.util.List;

@Data
public class TlxUser {
    Integer count = 0;
    List<UserDetail> userInfo;
}
