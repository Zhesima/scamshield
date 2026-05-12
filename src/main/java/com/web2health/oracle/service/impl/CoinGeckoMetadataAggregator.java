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

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CoinGecko 元数据聚合器实现
 *
 * 接口：GET /coins/{platform}/contract/{contract_address}
 * 文档：https://docs.coingecko.com/reference/coins-contract-address
 *
 * 限频：免费 Demo Pro 30次/分钟、10000次/月，需要 x-cg-demo-api-key
 * 升级：Pro 版改 base URL 为 https://pro-api.coingecko.com/api/v3
 *
 * Discord guild_id 解析：CoinGecko 返回的是邀请 URL（discord.gg/xxx），
 * 自动调用 DiscordInviteResolver 解析为真实 guild_id 写入 metadata
 */
@Slf4j
@Component("coingeckoAggregator")
@RequiredArgsConstructor
public class CoinGeckoMetadataAggregator implements MetadataAggregatorService {

    @Qualifier("coingeckoWebClient")
    private final WebClient coingeckoWebClient;

    private final DiscordInviteResolver discordInviteResolver;

    /** 完整 owner/repo 形式：github.com/foo/bar */
    private static final Pattern GITHUB_OWNER_REPO_PATTERN =
            Pattern.compile("github\\.com/([^/?#]+)/([^/?#]+)");
    /** 仅组织/用户：github.com/foo （CoinGecko 有时只填这种）*/
    private static final Pattern GITHUB_OWNER_ONLY_PATTERN =
            Pattern.compile("github\\.com/([^/?#]+)/?$");

    @Override
    public Optional<ProjectMetadata> fetchByContract(int chainId, String tokenAddress) {
        Optional<String> platform = ChainPlatformMapper.toCoinGeckoPlatform(chainId);
        if (platform.isEmpty()) {
            log.warn("不支持的链 ID: {}（CoinGecko 没有对应 platform 映射）", chainId);
            return Optional.empty();
        }

        String address = tokenAddress.toLowerCase();
        log.info("CoinGecko 反查项目元数据: platform={}, address={}", platform.get(), address);

        try {
            JsonNode body = coingeckoWebClient.get()
                    .uri("/coins/{platform}/contract/{address}", platform.get(), address)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (body == null) {
                return Optional.empty();
            }
            return Optional.of(parseMetadata(body));

        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("CoinGecko 找不到合约: chain={}, address={}", chainId, address);
                return Optional.empty();
            }
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.error("CoinGecko 限频，需要升级套餐或加大缓存 TTL");
            }
            throw new RuntimeException("CoinGecko API 调用失败: HTTP " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            throw new RuntimeException("CoinGecko 元数据采集异常", ex);
        }
    }

    private ProjectMetadata parseMetadata(JsonNode body) {
        ProjectMetadata.ProjectMetadataBuilder builder = ProjectMetadata.builder()
                .source("coingecko")
                .coingeckoId(text(body, "id"))
                .name(text(body, "name"));

        // symbol 通常小写，统一转大写
        String symbol = text(body, "symbol");
        if (symbol != null) {
            builder.symbol(symbol.toUpperCase());
        }

        // 描述：取英文版
        JsonNode desc = body.path("description").path("en");
        if (desc.isTextual() && !desc.asText().isBlank()) {
            String d = desc.asText();
            // 截断超长描述（聚合器有时返回数千字）
            builder.description(d.length() > 2000 ? d.substring(0, 2000) : d);
        }

        // Logo
        JsonNode image = body.path("image");
        builder.logoUrl(firstNonBlank(text(image, "large"), text(image, "small"), text(image, "thumb")));

        JsonNode links = body.path("links");

        // 官网（homepage 是数组，取第一个非空）
        builder.website(firstFromArray(links.path("homepage")));

        // GitHub repos（链接数组）
        // CoinGecko 数据质量参差：可能是 github.com/foo/bar（完整），也可能是 github.com/foo（仅组织）
        // 优先取完整 owner/repo；若全是组织 URL，至少把 owner 存下，repo 留空待人工补
        JsonNode githubRepos = links.path("repos_url").path("github");
        if (githubRepos.isArray()) {
            String fallbackOwner = null;
            for (JsonNode repo : githubRepos) {
                String url = repo.asText("");
                Matcher mFull = GITHUB_OWNER_REPO_PATTERN.matcher(url);
                if (mFull.find()) {
                    builder.githubOwner(mFull.group(1));
                    builder.githubRepo(mFull.group(2).replaceAll("\\.git$", ""));
                    fallbackOwner = null;
                    break;
                }
                if (fallbackOwner == null) {
                    Matcher mOwnerOnly = GITHUB_OWNER_ONLY_PATTERN.matcher(url);
                    if (mOwnerOnly.find()) {
                        fallbackOwner = mOwnerOnly.group(1);
                    }
                }
            }
            if (fallbackOwner != null) {
                builder.githubOwner(fallbackOwner);
                // githubRepo 留空：仅有 owner 时无法采集 dev_activity，需人工补 repo
            }
        }

        // Twitter handle
        builder.twitterHandle(text(links, "twitter_screen_name"));

        // Discord 邀请 URL → 解析 guild_id
        String discordInvite = firstFromArray(links.path("chat_url"));
        if (discordInvite != null && discordInvite.contains("discord")) {
            discordInviteResolver.resolveGuildId(discordInvite)
                    .ifPresent(builder::discordGuildId);
        }

        // 社交平台指标（community_data）
        JsonNode community = body.path("community_data");
        builder.twitterFollowers(intOrNull(community, "twitter_followers"));
        builder.telegramUsers(intOrNull(community, "telegram_channel_user_count"));
        builder.redditSubscribers(intOrNull(community, "reddit_subscribers"));

        // 项目分类（取第一个非空类别）
        JsonNode categories = body.path("categories");
        if (categories.isArray()) {
            for (JsonNode c : categories) {
                String s = c.asText("");
                if (!s.isBlank()) {
                    builder.category(s);
                    break;
                }
            }
        }

        return builder.build();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isNumber() ? v.asInt() : null;
    }

    // ── helper ────────────────────────────────────────────────────────────────
    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private String firstFromArray(JsonNode arr) {
        if (!arr.isArray()) return null;
        for (JsonNode n : arr) {
            String s = n.asText("");
            if (!s.isBlank()) return s;
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
