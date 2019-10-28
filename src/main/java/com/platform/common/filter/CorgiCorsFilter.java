package com.platform.common.filter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

public class CorgiCorsFilter extends org.springframework.web.filter.CorsFilter {


    public CorgiCorsFilter(String allowedOrigins, Long maxAge) {
        super(configurationSource(allowedOrigins, maxAge));
    }

    private static UrlBasedCorsConfigurationSource configurationSource(String allowedOrigins, Long maxAge) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOrigin(allowedOrigins);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(maxAge);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}