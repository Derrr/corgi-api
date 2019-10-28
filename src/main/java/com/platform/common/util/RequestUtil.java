package com.platform.common.util;

import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class RequestUtil {
    public static String BELLETONNE = "belletone";

    public static String FASHION = "fashion";

    public static String TOPSPORTS = "topsports";

    public static String MULTI = "multi";

    public static HttpServletRequest getRequest() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        ServletRequestAttributes sra = (ServletRequestAttributes) ra;
        HttpServletRequest request = sra.getRequest();
        return request;
    }

    public static String getUserId() {
        LoginUser loginUser = (LoginUser) getRequest().getAttribute("login_user");
        if (StringUtils.isEmpty(loginUser.getStaffId())) {
            return loginUser.getUserId();
        }
        return loginUser.getStaffId();
    }

    public static LoginUser getLoginUser() {
        LoginUser loginUser = (LoginUser) getRequest().getAttribute("login_user");
        return loginUser;
    }

    public static String getUserName() {
        LoginUser loginUser = (LoginUser) getRequest().getAttribute("login_user");
        if (loginUser != null) {
            return loginUser.getNickname();
        }
        return null;
    }

    public static String getAppname() {
        String appname = getRequest().getHeader("appname");
        if (StringUtils.isEmpty(appname)) {
            appname = "topsports";
        }
        return appname;
    }

    public static String getSource(){
        String source = getRequest().getHeader("package");
        if(StringUtils.isEmpty(source)){
            source = getRequest().getHeader("appname");;
        }
        if (StringUtils.isEmpty(source)) {
            source = "topsports";
        }
        return source;
    }

    public static String getPackage(){
        String packageStr = getRequest().getHeader("package");
        if(StringUtils.isEmpty(packageStr)){
            return "";
        }
        return packageStr;
    }

    public static String getPortal() {
        LoginUser loginUser = (LoginUser) getRequest().getAttribute("login_user");
        return loginUser.getPortal();
    }

    public static void setPortal(String portal){
        LoginUser loginUser = (LoginUser) getRequest().getAttribute("login_user");
        if(loginUser == null){
            loginUser = new LoginUser();
        }
        loginUser.setPortal(portal);
    }

    public static String getAPPID() {
        return (String) getRequest().getAttribute("APP_ID");
    }
}
