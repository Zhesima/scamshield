package com.web2health.oracle.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    public static final String HEALTH_SCORE_CACHE = "healthScore";
    public static final String RUG_SIGNALS_CACHE = "rugSignals";

    @Value("${cache.health-score-ttl:3600}")
    private long healthScoreTtl;

    @Value("${cache.rug-signals-ttl:900}")
    private long rugSignalsTtl;

    @Bean
    public CacheManager cacheManager() {
        CaffeineCache healthScoreCache = new CaffeineCache(
                HEALTH_SCORE_CACHE,
                Caffeine.newBuilder()
                        .expireAfterWrite(healthScoreTtl, TimeUnit.SECONDS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        CaffeineCache rugSignalsCache = new CaffeineCache(
                RUG_SIGNALS_CACHE,
                Caffeine.newBuilder()
                        .expireAfterWrite(rugSignalsTtl, TimeUnit.SECONDS)
                        .maximumSize(500)
                        .recordStats()
                        .build()
        );

        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(healthScoreCache, rugSignalsCache));
        return cacheManager;
    }
}
