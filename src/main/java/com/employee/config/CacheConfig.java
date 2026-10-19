package com.employee.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 缓存基础设施（Phase 0-8）。
 * 一级：Caffeine 本地缓存（进程内、无序列化开销，适合每请求命中的热点小数据）。
 * 二级/分布式：Redis（跨实例共享，后续为部门树/排班/统计等接入；单机部署下 Caffeine 已覆盖主要收益）。
 *
 * 约定：缓存 key 必须自带维度，用户相关统计必须带 userId 前缀，防止不同权重串数据（越权）。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_PERM_ROLE = "perm:role";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // 默认策略：最多 2000 条、写后 10 分钟过期
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        // 首页统计类：短 TTL 60 秒，避免脏读又扛高频
        manager.registerCustomCache("dash:stats",
                Caffeine.newBuilder().maximumSize(1000).expireAfterWrite(60, TimeUnit.SECONDS).build());
        return manager;
    }
}
