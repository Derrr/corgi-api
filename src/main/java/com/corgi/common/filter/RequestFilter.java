package com.corgi.common.filter;

import com.alibaba.fastjson.JSONObject;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.JsonResult;
import com.corgi.common.constant.Constants;
import com.corgi.common.util.JWTUtils;
import com.corgi.entity.JwtUser;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.UUID;

@Slf4j
@WebFilter(filterName = "myFilter", urlPatterns = "/**")
public class RequestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (((HttpServletRequest) servletRequest).getMethod().equals("OPTIONS")) {
            servletResponse.setContentType("application/json;charset=UTF-8");
            return;
        }
        long time = System.currentTimeMillis();
        String jwt = ((HttpServletRequest) servletRequest).getHeader(JWTUtils.JWT_HEADER);
        if (!StringUtils.isEmpty(jwt) && !checkURI(servletRequest, "update_user_position")) {
            DecodedJWT decodedJWT;
            try {
                decodedJWT = JWTUtils.verifyToken(jwt);
            } catch (Exception e) {
                log.error(e.getMessage());
                JsonResult jsonResult = new JsonResult("");
                jsonResult.setCode(Constants.JWT_ERROR_CODE);
                jsonResult.setMessage(e.getMessage());
                servletResponse.getWriter().write(JSONObject.toJSONString(jsonResult));
                servletResponse.setContentType("application/json;charset=UTF-8");
                return;
            }
            JwtUser user = JwtUser.builder()
                    .userId(decodedJWT.getClaim(JwtUser.USER_ID).asString())
                    .version(decodedJWT.getClaim(JwtUser.VERSION).asString())
                    .build();
            servletRequest.setAttribute(JWTUtils.JWT_USER, user);
        } else if (checkURI(servletRequest, "login")
                || checkURI(servletRequest, "send_code")
                || checkURI(servletRequest, "update_user_position")) {
            log.info("into none jwt uri...." + ((HttpServletRequest) servletRequest).getRequestURI());
        } else {
            JsonResult jsonResult = new JsonResult("");
            jsonResult.setCode(Constants.PARAMETER_ERROR_CODE);
            jsonResult.setMessage("嘿 小哥哥！我们的攻程湿们为了大家更好的面基体验，已经更新了版本哦，速去下载更新吧！");
            servletResponse.setContentType("application/json;charset=UTF-8");
            servletResponse.setCharacterEncoding("UTF-8");
            servletResponse.getWriter().write(JSONObject.toJSONString(jsonResult));
            return;
        }
        MDC.put("reqId", UUID.randomUUID().toString());
        MDC.put("usrID", "");
        filterChain.doFilter(servletRequest, servletResponse);
        String url = ((HttpServletRequest) servletRequest).getRequestURI();
        log.info("time spent...{}:{}ms", url, (System.currentTimeMillis() - time));
    }


    @Override
    public void destroy() {

    }

    private boolean checkURI(ServletRequest request, String path) {
        return ((HttpServletRequest) request).getRequestURI().contains(path);
    }
}
