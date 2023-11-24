package com.corgi.job;

import com.alibaba.dubbo.config.annotation.Reference;
import com.corgi.service.EasemobService;
import com.corgi.user.api.CorgiUserService;
import com.corgi.user.entity.UserDetail;
import com.corgi.user.entity.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


@Component
@Slf4j
public class UserJob {
    @Reference
    private CorgiUserService corgiUserService;
    @Autowired
    private EasemobService easemobService;

    //@Async
    //@Scheduled(fixedRate = 24 * 3600 * 1000)
    public void dayRefresh() {
        List<UserProfile> profileList;
        int page = 1;
        int pageSize = 100;
        do {
            log.info("doing page:" + page);
            profileList = corgiUserService.getBasicUserDetailByPage(page, pageSize);
            page++;
            for (UserProfile userProfile : profileList) {
                UserDetail userDetail = new UserDetail();
                userDetail.setNickname(userProfile.getNickname());
                userDetail.setAvatar(userProfile.getAvatar());
                userDetail.setAvatarCheckStatus(userProfile.getAvatarCheckStatus());
                easemobService.refreshUser(userDetail);
            }
        } while (!CollectionUtils.isEmpty(profileList));

    }
}
