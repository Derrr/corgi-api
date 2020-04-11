package com.corgi.common.util;

import com.corgi.entity.JwtUser;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class RequestUtil {
    public static HttpServletRequest getRequest() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) ra;
        HttpServletRequest request = sra.getRequest();
        return request;
    }

    public static boolean hasUserId() {
        JwtUser user = (JwtUser) getRequest().getAttribute("jwtUser");
        return user != null && !StringUtils.isEmpty(user.getUserId());
    }

    public static String getUserId() {
        if (hasUserId()) {
            JwtUser user = (JwtUser) getRequest().getAttribute("jwtUser");
            return user.getUserId();
        }
        return "";
    }

    public static String getJwt(){
        return getRequest().getHeader("jwt");
    }
}
