package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * DexScreener API 客户端（CN 大陆受限，需走代理）
 *
 * 接口：GET https://api.dexscreener.com/latest/dex/tokens/{address}
 * 返回该地址在所有 DEX 上的交易对列表，每对含 liquidity / volume / price
 *
 * 限频：60 req/min（无需 API key）
 * 注意：DexScreener 按 contract address 查所有链上匹配，返回里 chainId 字段需要过滤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DexScreenerClient {

    @Qualifier("dexscreenerWebClient")
    private final WebClient client;

    /** 拉所有交易对，过滤指定 chainId，按流动性求和 */
    public DexAggregate fetchAggregate(int chainId, String tokenAddress) {
        String chainName = chainIdToDexScreener(chainId);
        if (chainName == null) {
            log.warn("DexScreener 不支持的链 ID: {}", chainId);
            return DexAggregate.empty();
        }

        try {
            JsonNode body = client.get()
                    .uri("/latest/dex/tokens/{addr}", tokenAddress.toLowerCase())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (body == null) return DexAggregate.empty();
            JsonNode pairs = body.path("pairs");
            if (!pairs.isArray() || pairs.size() == 0) {
                log.info("DexScreener 没有该 token 的交易对: chainId={}, addr={}", chainId, tokenAddress);
                return DexAggregate.empty();
            }

            double liquidity = 0.0;
            double volume24h = 0.0;
            double priceUsd = 0.0;
            String primaryPair = null;
            double maxPairLiquidity = 0.0;

            for (JsonNode p : pairs) {
                String pairChain = p.path("chainId").asText("");
                if (!pairChain.equalsIgnoreCase(chainName)) continue;

                double pairLiq = p.path("liquidity").path("usd").asDouble(0.0);
                double pairVol = p.path("volume").path("h24").asDouble(0.0);
                liquidity += pairLiq;
                volume24h += pairVol;

                if (pairLiq > maxPairLiquidity) {
                    maxPairLiquidity = pairLiq;
                    priceUsd = p.path("priceUsd").asDouble(0.0);
                    primaryPair = p.path("baseToken").path("symbol").asText("?")
                            + "/" + p.path("quoteToken").path("symbol").asText("?");
                }
            }

            return new DexAggregate(liquidity, volume24h, priceUsd, primaryPair);

        } catch (Exception ex) {
            log.warn("DexScreener 调用失败: {}", ex.getMessage());
            return DexAggregate.empty();
        }
    }

    /** chainId → DexScreener chain 名（与 DefiLlama 名映射类似但有差别）*/
    private String chainIdToDexScreener(int chainId) {
        return switch (chainId) {
            case 1 -> "ethereum";
            case 10 -> "optimism";
            case 56 -> "bsc";
            case 137 -> "polygon";
            case 8453 -> "base";
            case 42161 -> "arbitrum";
            case 43114 -> "avalanche";
            case 250 -> "fantom";
            default -> null;
        };
    }

    public record DexAggregate(double liquidityUsd, double volume24hUsd, double priceUsd, String primaryPair) {
        public static DexAggregate empty() {
            return new DexAggregate(0.0, 0.0, 0.0, null);
        }
        public boolean hasData() {
            return liquidityUsd > 0 || volume24hUsd > 0;
        }
    }
}
