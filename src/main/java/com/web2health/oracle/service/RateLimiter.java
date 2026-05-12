package com.web2health.oracle.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.web2health.oracle.config.X402Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单的内存级 IP 限流器（Caffeine）
 *
 * 设计：
 *   - 滑动窗口：windowSeconds 内每 IP 允许 quota 次免费请求
 *   - Caffeine.expireAfterWrite 自动清理过期窗口
 *   - 单实例部署够用；生产多实例需换 Redis（暂不实现）
 *
 * 局限：
 *   - 没有 cluster sync（多实例时配额各自计算）
 *   - 没有滑动精度（一个 60s 窗口被消耗完后，下一秒重置）
 *     生产改成 token bucket / sliding window log 更精确
 */
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private final X402Properties props;

    private volatile Cache<String, AtomicInteger> counters;

    /** 检查并消耗一次配额；true=未超限可放行；false=超限需付费 */
    public boolean tryAcquire(String clientIp) {
        if (counters == null) {
            initCache();
        }
        AtomicInteger counter = counters.get(clientIp, k -> new AtomicInteger(0));
        return counter.incrementAndGet() <= props.getFreeTierQuota();
    }

    /** 当前 IP 已消耗的配额数（用于响应头展示）*/
    public int currentUsage(String clientIp) {
        if (counters == null) return 0;
        AtomicInteger c = counters.getIfPresent(clientIp);
        return c == null ? 0 : c.get();
    }

    private synchronized void initCache() {
        if (counters != null) return;
        counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getFreeTierWindowSeconds()))
                .maximumSize(10_000)
                .build();
    }
}
