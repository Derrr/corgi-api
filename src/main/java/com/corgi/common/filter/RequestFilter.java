package com.corgi.common.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

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
        MDC.put("reqId", ((HttpServletRequest) servletRequest).getHeader("dasuan-req-id"));
        MDC.put("usrID", "");
        filterChain.doFilter(servletRequest, servletResponse);
        log.info("time spent...{}:{}ms", ((HttpServletRequest) servletRequest).getRequestURI(), (System.currentTimeMillis() - time));
    }

    @Override
    public void destroy() {

    }
}
