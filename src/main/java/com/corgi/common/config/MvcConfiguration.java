package com.corgi.common.config;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MvcConfiguration implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter fastConverter = new FastJsonHttpMessageConverter();
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        SerializerFeature[] list = new SerializerFeature[]{SerializerFeature.BrowserSecure, SerializerFeature.MapSortField};
        fastJsonConfig.setSerializerFeatures(list);
        fastConverter.setFastJsonConfig(fastJsonConfig);
        fastConverter.setSupportedMediaTypes(new ArrayList<MediaType>() {{
            add(MediaType.APPLICATION_JSON_UTF8);
        }});
        converters.add(fastConverter);
    }

}
