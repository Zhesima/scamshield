package com.web2health.oracle.service.impl;

import com.web2health.oracle.dto.response.T2HealthScoreResponse;
import com.web2health.oracle.service.OnchainOracleWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * t3.enabled=false 时的占位实现：调 publish 直接抛 IllegalStateException
 * Admin endpoint 应先用 isEnabled() 检查
 */
@Service
@ConditionalOnProperty(name = "t3.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledOnchainOracleWriter implements OnchainOracleWriter {

    @Override
    public String publish(T2HealthScoreResponse resp) {
        throw new IllegalStateException(
                "T3 未启用：请在 application.yml 设置 t3.enabled=true 并配置 health-oracle-address / publisher-private-key");
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
