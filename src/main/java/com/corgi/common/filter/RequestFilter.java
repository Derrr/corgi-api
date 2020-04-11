package com.corgi.common.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.corgi.common.util.JWTUtils;
import com.corgi.entity.JwtUser;
import com.corgi.exception.PermissionException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
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
        String jwt = ((HttpServletRequest) servletRequest).getHeader("jwt");
        if (!StringUtils.isEmpty(jwt)) {
            DecodedJWT decodedJWT = JWTUtils.verifyToken(jwt);
            JwtUser user = JwtUser.builder()
                    .userId(decodedJWT.getClaim("userId").asString())
                    .version(decodedJWT.getClaim("version").asString())
                    .build();
            servletRequest.setAttribute("jwtUser", user);
        }
        MDC.put("reqId", UUID.randomUUID().toString());
        MDC.put("usrID", "");
        filterChain.doFilter(servletRequest, servletResponse);
        log.info("time spent...{}:{}ms", ((HttpServletRequest) servletRequest).getRequestURI(), (System.currentTimeMillis() - time));
    }

    @Override
    public void destroy() {

    }
}
