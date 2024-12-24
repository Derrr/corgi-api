package com.corgi.entity.tlx;

import com.corgi.user.entity.TlxActivity;
import lombok.Data;

@Data
public class TlxActivityList extends TlxActivity {
    private String avatar;
    private String nickname;
}
