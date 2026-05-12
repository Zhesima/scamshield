package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.web2health.oracle.dto.aggregator.ProjectMetadata;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DefiLlama /protocols 全量列表缓存
 *
 * /protocols 接口返回所有 DeFi 协议的元数据（含 GitHub / Twitter / 描述 / logo / category 等）
 * 一次拉取通常 1.5MB+ JSON，~5000 协议
 *
 * 索引方式：chain:address (lowercase) → ProjectMetadata
 *   单个协议可能在多链有多个合约，全部加入索引
 *
 * 刷新策略：
 *   - 启动后异步拉取（不阻塞应用启动）
 *   - 每 24h 刷新一次（@Scheduled）
 *   - 失败容忍：保留旧数据，下个周期重试
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableAsync
public class DefiLlamaProtocolsCache {

    @Qualifier("defillamaWebClient")
    private final WebClient defillamaWebClient;

    /** DefiLlama protocols 列表的实际域名是 api.llama.fi，与 coins.llama.fi 不同 */
    @Value("${defillama.base-url:https://api.llama.fi}")
    private String defillamaApiBaseUrl;

    /** 索引：chain:address (lowercase) → 元数据 */
    private final Map<String, ProjectMetadata> index = new ConcurrentHashMap<>();

    private static final Pattern GITHUB_REPO_PATTERN =
            Pattern.compile("github\\.com/([^/?#]+)(?:/([^/?#]+))?");

    @PostConstruct
    public void onStartup() {
        // 异步拉取，避免拖慢应用启动
        refreshAsync();
    }

    /** 每 24 小时刷新一次（凌晨 3 点错峰，避开 DefiLlama 维护时段）*/
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledRefresh() {
        refreshAsync();
    }

    @Async
    public void refreshAsync() {
        try {
            log.info("DefiLlama /protocols 开始全量刷新（异步）");
            // 用单独 WebClient 调用，因为路径在 api.llama.fi 而不是 coins.llama.fi
            // /protocols 响应 ~1.5MB，加大内存缓冲（默认 256KB 不够）
            WebClient client = WebClient.builder()
                    .baseUrl(defillamaApiBaseUrl)
                    .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                    .build();
            JsonNode protocols = client.get()
                    .uri("/protocols")
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (protocols == null || !protocols.isArray()) {
                log.warn("DefiLlama /protocols 响应非数组，跳过刷新");
                return;
            }

            Map<String, ProjectMetadata> newIndex = new ConcurrentHashMap<>();
            int parsed = 0;
            for (JsonNode p : protocols) {
                ProjectMetadata meta = parseProtocol(p);
                if (meta == null) continue;
                // address 字段格式：可能 "ethereum:0x..." / "0x..."（默认 ethereum） / null
                String addrField = p.path("address").asText("");
                if (addrField.isBlank() || "-".equals(addrField)) continue;
                String key = normalizeAddressKey(addrField);
                if (key != null) {
                    newIndex.put(key, meta);
                    parsed++;
                }
            }
            // 原子替换，旧索引在新索引完整构建好之前一直可用
            index.clear();
            index.putAll(newIndex);
            log.info("DefiLlama /protocols 刷新完成: 总数={}, 已索引={}", protocols.size(), parsed);
        } catch (Exception ex) {
            log.warn("DefiLlama /protocols 刷新失败，保留旧缓存: {}", ex.getMessage());
        }
    }

    /** 按 (chainId, address) 查询（chainId 转 DefiLlama chain 名）*/
    public Optional<ProjectMetadata> findByContract(int chainId, String tokenAddress) {
        String chain = chainIdToDefiLlama(chainId);
        if (chain == null) return Optional.empty();
        String key = chain + ":" + tokenAddress.toLowerCase();
        return Optional.ofNullable(index.get(key));
    }

    public int size() {
        return index.size();
    }

    // ── 解析单个 protocol 节点 ───────────────────────────────────────────────
    private ProjectMetadata parseProtocol(JsonNode p) {
        String name = text(p, "name");
        if (name == null) return null;

        ProjectMetadata.ProjectMetadataBuilder b = ProjectMetadata.builder()
                .source("defillama")
                .name(name)
                .symbol(upperOrNull(text(p, "symbol")))
                .description(text(p, "description"))
                .website(text(p, "url"))
                .logoUrl(text(p, "logo"))
                .twitterHandle(text(p, "twitter"))
                .category(text(p, "category"))
                .defillamaId(text(p, "slug"))
                .coingeckoId(text(p, "gecko_id"));

        // github 字段在 DefiLlama 是数组（owner 列表，没具体 repo）
        JsonNode githubNode = p.path("github");
        if (githubNode.isArray() && githubNode.size() > 0) {
            String owner = githubNode.get(0).asText("");
            if (!owner.isBlank()) {
                Matcher m = GITHUB_REPO_PATTERN.matcher(owner);
                if (m.find()) {
                    b.githubOwner(m.group(1));
                    if (m.group(2) != null) {
                        b.githubRepo(m.group(2).replaceAll("\\.git$", ""));
                    }
                } else {
                    // DefiLlama 通常只填用户名，没 URL
                    b.githubOwner(owner.replaceAll("[^A-Za-z0-9._-]", ""));
                }
            }
        }

        return b.build();
    }

    /** "ethereum:0x..." → "ethereum:0x..." (lowercase) ；裸地址 0x... 默认 ethereum */
    private String normalizeAddressKey(String addrField) {
        if (addrField == null || addrField.isBlank()) return null;
        String s = addrField.toLowerCase().trim();
        if (s.startsWith("0x") && !s.contains(":")) {
            return "ethereum:" + s;
        }
        if (s.contains(":")) {
            return s;
        }
        return null;
    }

    /** chainId 数字 → DefiLlama chain 名（与 DefiLlamaMetadataAggregator 共享映射）*/
    private String chainIdToDefiLlama(int chainId) {
        return switch (chainId) {
            case 1 -> "ethereum";
            case 10 -> "optimism";
            case 56 -> "bsc";
            case 137 -> "polygon";
            case 8453 -> "base";
            case 42161 -> "arbitrum";
            case 43114 -> "avax";
            case 250 -> "fantom";
            case 100 -> "xdai";
            case 534352 -> "scroll";
            case 59144 -> "linea";
            case 324 -> "era";
            default -> null;
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private String upperOrNull(String s) {
        return s == null ? null : s.toUpperCase();
    }
}
