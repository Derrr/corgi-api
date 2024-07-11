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
        JwtUser user = (JwtUser) getRequest().getAttribute(JWTUtils.JWT_USER);
        return user != null && !StringUtils.isEmpty(user.getUserId()) && !JWTUtils.ADMIN_ID.equals(user.getUserId());
    }

    public static String getUserId() {
        if (hasUserId()) {
            JwtUser user = (JwtUser) getRequest().getAttribute(JWTUtils.JWT_USER);
            return user.getUserId();
        }
        return "";
    }

    public static boolean hasVersion() {
        String version = getRequest().getHeader("version");
        return !StringUtils.isEmpty(version);
    }

    public static String getVersion() {
        return getRequest().getHeader("version");
    }

    public static String getPackageName() {
        String packageName = getRequest().getHeader("packageName");
        if (StringUtils.isEmpty(packageName)) {
            return "";
        }
        return packageName;
    }

    public static String getIP() {
        return IPUtil.getIpAddr(getRequest());
    }

    public static String getChannel() {
        return getRequest().getHeader("channel");
    }

    public static String getJwt() {
        return getRequest().getHeader(JWTUtils.JWT_HEADER);
    }
}
