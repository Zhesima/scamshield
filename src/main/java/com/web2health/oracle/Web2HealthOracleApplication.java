package com.web2health.oracle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling  // DefiLlamaProtocolsCache 用 @Scheduled cron 每日刷新
public class Web2HealthOracleApplication {

    public static void main(String[] args) {
        SpringApplication.run(Web2HealthOracleApplication.class, args);
    }
}
