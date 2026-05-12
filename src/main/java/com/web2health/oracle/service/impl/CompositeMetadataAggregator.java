package com.web2health.oracle.service.impl;

import com.web2health.oracle.dto.aggregator.ProjectMetadata;
import com.web2health.oracle.service.MetadataAggregatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 复合元数据聚合器：CoinGecko 主，DefiLlama 备
 *
 * 调用顺序：
 *   1) CoinGecko：拉到完整元数据（含 GitHub / Discord / Twitter）就直接返回
 *   2) CoinGecko 异常（CN 大陆被墙 / 限频 / 5xx）→ DefiLlama 兜底（仅 symbol）
 *   3) 两者都没数据 → Optional.empty()（HealthScoreService 抛 404）
 *
 * 注意：DefiLlama 兜底只能拿到 symbol，T1 score 会因 GitHub / Discord 缺失大幅降低
 *      生产环境强烈建议保证 CoinGecko 可达（配代理或部署到非 CN 节点）
 */
@Slf4j
@Service
@Primary
public class CompositeMetadataAggregator implements MetadataAggregatorService {

    private final MetadataAggregatorService coingecko;
    private final MetadataAggregatorService defillama;

    public CompositeMetadataAggregator(
            @Qualifier("coingeckoAggregator") MetadataAggregatorService coingecko,
            @Qualifier("defillamaAggregator") MetadataAggregatorService defillama) {
        this.coingecko = coingecko;
        this.defillama = defillama;
    }

    @Override
    public Optional<ProjectMetadata> fetchByContract(int chainId, String tokenAddress) {
        // 主源
        try {
            Optional<ProjectMetadata> primary = coingecko.fetchByContract(chainId, tokenAddress);
            if (primary.isPresent()) {
                return primary;
            }
            // 主源明确返回找不到 → 仍尝试备援（DefiLlama 索引可能更全）
            log.info("CoinGecko 未命中，尝试 DefiLlama 备援: chainId={}, token={}", chainId, tokenAddress);
        } catch (RuntimeException ex) {
            log.warn("CoinGecko 调用异常，降级到 DefiLlama: reason={}", ex.getMessage());
        }

        // 备援
        try {
            return defillama.fetchByContract(chainId, tokenAddress);
        } catch (RuntimeException ex) {
            log.error("DefiLlama 也调用失败: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }
}
