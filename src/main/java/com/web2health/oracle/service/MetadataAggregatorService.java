package com.web2health.oracle.service;

import com.web2health.oracle.dto.aggregator.ProjectMetadata;

import java.util.Optional;

/**
 * 项目元数据聚合器
 *
 * 职责：根据 (chainId, tokenAddress) 反查项目元数据
 * 实现：T1 用 CoinGecko 主源；DefiLlama / 链上 ENS 解析等后续再叠加
 *
 * 调用语义：
 *   - 找到 → 返回 Optional.of(ProjectMetadata)
 *   - 第三方说项目不存在（404） → Optional.empty()
 *   - 第三方故障（5xx / timeout） → 抛 RuntimeException
 */
public interface MetadataAggregatorService {

    Optional<ProjectMetadata> fetchByContract(int chainId, String tokenAddress);
}
