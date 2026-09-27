package com.hrportal.config;

import com.hrportal.security.FirstLoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FirstLoginInterceptor firstLoginInterceptor;

    public WebConfig(FirstLoginInterceptor firstLoginInterceptor) {
        this.firstLoginInterceptor = firstLoginInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(firstLoginInterceptor)
                .excludePathPatterns("/login", "/logout", "/error", "/error/**", "/css/**",
                        "/actuator/**", "/api/**", "/swagger-ui/**", "/v3/api-docs/**");
    }
}
