package com.web2health.oracle.service;

import com.web2health.oracle.dto.response.T2HealthScoreResponse;

/**
 * T3 上链推送服务
 *
 * 把 T2 评分（含签名）作为交易广播到 HealthOracle 合约
 *
 * 实现：
 *   - DisabledOracleWriter：t3.enabled=false 时空实现
 *   - DefaultOnchainOracleWriter：调用合约 publishScore
 */
public interface OnchainOracleWriter {

    /**
     * 上链发布
     *
     * @param resp T2 评分结果（含 EIP-712 签名）
     * @return 交易 hash（"0x..."），失败抛 RuntimeException
     */
    String publish(T2HealthScoreResponse resp);

    /** 是否启用（用于 admin 端点判断）*/
    boolean isEnabled();
}
