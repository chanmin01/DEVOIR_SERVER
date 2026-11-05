package com.benly.pt.config;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class PtWebConfig implements WebMvcConfigurer {
    private final PtLegacyApiGuard guard;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard)
                .addPathPatterns("/api/v1/sessions/**")
                .excludePathPatterns("/api/v1/sessions/*/cancel");
    }
}