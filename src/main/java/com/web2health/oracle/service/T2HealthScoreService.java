package com.web2health.oracle.service;

import com.web2health.oracle.dto.response.T2HealthScoreResponse;

/**
 * T2 Hard Score 服务
 *
 * 与 T1 完全独立：
 *   - 数据源全部链上（不依赖 GitHub / Discord / 聚合器）
 *   - 输出含 EIP-712 签名，消费方可独立验证
 *   - 不写 projects 表（T2 是无状态计算）
 */
public interface T2HealthScoreService {

    T2HealthScoreResponse computeAndSign(int chainId, String tokenAddress);
}
