package com.fishmatrix.user.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/v1/user/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCachePeriod(0);
        // 直连中台入口也必须禁用缓存，避免发布后仍加载旧的管理页脚本
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .setCachePeriod(0);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                    Object handler) {
                response.setHeader("Cache-Control", "no-store, max-age=0");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                return true;
            }
        }).addPathPatterns("/admin/**", "/api/v1/user/admin/**");
    }
}
