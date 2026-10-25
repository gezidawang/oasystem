package com.employee.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 跨域配置：允许前端开发服务器携带凭证访问。
 * 局域网真机调试：手机浏览器 Origin = http://192.168.x.x:5173，需加入白名单，
 * 否则 Spring 直接拒绝（Invalid CORS request → 403）。
 * ⚠ 生产环境应收紧为正式域名白名单（配合 Nginx 同源部署后甚至可全部关掉）。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // 本机 + 局域网私有段的 5173 开发服务（仅开发环境）
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://192.168.*:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // 滑动续期：允许浏览器 JS 读取续期响应头（缺失时前端 axios 拿不到 X-Renew-Token）
        config.setExposedHeaders(List.of("X-Renew-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
