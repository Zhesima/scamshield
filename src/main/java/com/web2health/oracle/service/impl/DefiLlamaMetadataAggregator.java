package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.web2health.oracle.dto.aggregator.ProjectMetadata;
import com.web2health.oracle.service.MetadataAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
import java.util.Optional;

/**
 * DefiLlama 元数据聚合器（CN 大陆备援源）
 *
 * 双层数据源（按优先级）：
 *   1. /protocols 缓存（DefiLlamaProtocolsCache）：含 GitHub / Twitter / 描述 / logo / category
 *      仅 ~5000 个 DeFi 协议，覆盖头部项目
 *   2. coins.llama.fi/prices/current/{chain}:{address}（fallback）：仅 symbol / decimals / price
 *      覆盖几乎所有 token，但元数据极薄
 *
 * 仍不如 CoinGecko 完整，但 CN 大陆无需代理可直连
 */
@Slf4j
@Component("defillamaAggregator")
@RequiredArgsConstructor
public class DefiLlamaMetadataAggregator implements MetadataAggregatorService {

    @Qualifier("defillamaWebClient")
    private final WebClient defillamaWebClient;

    private final DefiLlamaProtocolsCache protocolsCache;

    /** chainId → DefiLlama chain 标识 */
    private static final Map<Integer, String> CHAIN_ID_TO_DEFILLAMA = Map.ofEntries(
            Map.entry(1, "ethereum"),
            Map.entry(10, "optimism"),
            Map.entry(56, "bsc"),
            Map.entry(137, "polygon"),
            Map.entry(8453, "base"),
            Map.entry(42161, "arbitrum"),
            Map.entry(43114, "avax"),
            Map.entry(250, "fantom"),
            Map.entry(100, "xdai"),
            Map.entry(534352, "scroll"),
            Map.entry(59144, "linea"),
            Map.entry(324, "era")
    );

    @Override
    public Optional<ProjectMetadata> fetchByContract(int chainId, String tokenAddress) {
        // 第一层：protocols 缓存（内存命中，0 网络调用，元数据丰富）
        Optional<ProjectMetadata> fromProtocols = protocolsCache.findByContract(chainId, tokenAddress);
        if (fromProtocols.isPresent()) {
            log.info("DefiLlama 命中 protocols 缓存: chainId={}, token={}", chainId, tokenAddress);
            return fromProtocols;
        }

        // 第二层：coins API（薄元数据 fallback）
        return fetchFromCoinsApi(chainId, tokenAddress);
    }

    private Optional<ProjectMetadata> fetchFromCoinsApi(int chainId, String tokenAddress) {
        String chain = CHAIN_ID_TO_DEFILLAMA.get(chainId);
        if (chain == null) {
            log.warn("DefiLlama 不支持的链 ID: {}", chainId);
            return Optional.empty();
        }

        String address = tokenAddress.toLowerCase();
        String coinKey = chain + ":" + address;
        log.info("DefiLlama coins API 反查: coin={}", coinKey);

        try {
            JsonNode body = defillamaWebClient.get()
                    .uri("/prices/current/{coin}", coinKey)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (body == null) return Optional.empty();
            JsonNode coin = body.path("coins").path(coinKey);
            if (coin.isMissingNode() || coin.isNull()) {
                log.info("DefiLlama coins API 找不到合约: {}", coinKey);
                return Optional.empty();
            }

            String symbol = coin.path("symbol").asText(null);
            if (symbol == null || symbol.isBlank()) return Optional.empty();

            // 仅有 symbol，name 用 symbol 占位
            return Optional.of(ProjectMetadata.builder()
                    .source("defillama")
                    .name(symbol)
                    .symbol(symbol.toUpperCase())
                    .build());

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new RuntimeException("DefiLlama coins API 调用失败: HTTP " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("DefiLlama coins API 异常", ex);
        }
    }
}
