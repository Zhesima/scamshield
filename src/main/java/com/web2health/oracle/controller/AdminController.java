package com.web2health.oracle.controller;

import com.web2health.oracle.domain.entity.Project;
import com.web2health.oracle.dto.request.ProjectOverrideRequest;
import com.web2health.oracle.dto.response.ProjectIdentity;
import com.web2health.oracle.dto.response.T2HealthScoreResponse;
import com.web2health.oracle.exception.ProjectNotFoundException;
import com.web2health.oracle.repository.ProjectRepository;
import com.web2health.oracle.service.OnchainOracleWriter;
import com.web2health.oracle.service.T2HealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.function.Consumer;

import static com.web2health.oracle.config.CacheConfig.HEALTH_SCORE_CACHE;

/**
 * Admin 运维接口
 *
 * 鉴权：所有端点要求 Header `X-Admin-Token` 与配置 `admin.api-token` 完全匹配
 *      未配置 token = 接口禁用（403）
 *
 * 主要场景：
 *   1. CoinGecko 数据质量参差时人工补全字段（如 ILV 没具体 GitHub repo）
 *   2. 人工锁定字段后，下次 auto-refresh 不覆盖
 *   3. 强制清缓存触发重新拉取元数据 + 评分
 *
 * 生产部署：
 *   - admin.api-token 用 32+ 位随机字符串
 *   - 网关层加 IP 白名单 / VPN 限制
 *   - 后续可换成 OAuth2 / JWT
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ProjectRepository projectRepository;
    private final T2HealthScoreService t2Service;
    private final OnchainOracleWriter onchainOracleWriter;

    @Value("${admin.api-token:}")
    private String adminApiToken;

    private static final Pattern EVM_ADDRESS = Pattern.compile("^0x[a-fA-F0-9]{40}$");

    // ── 端点 ──────────────────────────────────────────────────────────────────

    /**
     * 人工覆盖项目元数据
     * 任何被设置（非 null）的字段自动加入 manual_overrides 锁定列表
     */
    @PutMapping("/projects/{chainId}/{tokenAddress}/metadata")
    @Transactional
    @CacheEvict(value = HEALTH_SCORE_CACHE, allEntries = true)
    public ResponseEntity<ProjectIdentity> overrideMetadata(
            HttpServletRequest httpReq,
            @PathVariable int chainId,
            @PathVariable String tokenAddress,
            @RequestBody ProjectOverrideRequest req) {

        requireAdmin(httpReq);
        validateAddress(tokenAddress);

        String address = tokenAddress.toLowerCase();
        Project project = projectRepository.findByChainIdAndTokenAddress(chainId, address)
                .orElseThrow(() -> new ProjectNotFoundException(chainId, address));

        // 解锁字段（先于覆盖，避免本次新设的字段又被解锁）
        if (req.getUnlockFields() != null) {
            req.getUnlockFields().forEach(project::unlockField);
        }

        // 应用覆盖：传入非 null 即视为人工设置 → 锁定字段
        applyOverride(project, "name",          req.getName(),          project::setName);
        applyOverride(project, "symbol",        req.getSymbol(),        project::setSymbol);
        applyOverride(project, "description",   req.getDescription(),   project::setDescription);
        applyOverride(project, "website",       req.getWebsite(),       project::setWebsite);
        applyOverride(project, "logoUrl",       req.getLogoUrl(),       project::setLogoUrl);
        applyOverride(project, "githubOwner",   req.getGithubOwner(),   project::setGithubOwner);
        applyOverride(project, "githubRepo",    req.getGithubRepo(),    project::setGithubRepo);
        applyOverride(project, "discordGuildId",req.getDiscordGuildId(),project::setDiscordGuildId);
        applyOverride(project, "twitterHandle", req.getTwitterHandle(), project::setTwitterHandle);
        applyOverride(project, "category",      req.getCategory(),      project::setCategory);
        applyIntOverride(project, "twitterFollowers",  req.getTwitterFollowers(),  project::setTwitterFollowers);
        applyIntOverride(project, "telegramUsers",     req.getTelegramUsers(),     project::setTelegramUsers);
        applyIntOverride(project, "redditSubscribers", req.getRedditSubscribers(), project::setRedditSubscribers);

        // 任意人工设置 → metadata_source 改为 manual，刷新时间重置为现在
        if (project.getManualOverrides() != null && !project.getManualOverrides().isEmpty()) {
            project.setMetadataSource("manual");
        }
        project.setMetadataFetchedAt(LocalDateTime.now());

        Project saved = projectRepository.save(project);
        log.info("人工覆盖元数据完成: chainId={}, token={}, locks={}",
                chainId, address, saved.getManualOverrides());

        return ResponseEntity.ok(buildIdentity(saved));
    }

    /**
     * 强制清空响应缓存（Caffeine），不动 DB 元数据
     * 下次调用走 DB → 若 metadata_fetched_at 未过期，仍用 DB 缓存的元数据
     * 想强制重新拉聚合器，配合 PUT 一起用，或直接 SQL 把 metadata_fetched_at 置 NULL
     */
    @DeleteMapping("/projects/{chainId}/{tokenAddress}/cache")
    @CacheEvict(value = HEALTH_SCORE_CACHE, allEntries = true)
    public ResponseEntity<Void> evictCache(
            HttpServletRequest httpReq,
            @PathVariable int chainId,
            @PathVariable String tokenAddress) {

        requireAdmin(httpReq);
        validateAddress(tokenAddress);
        log.info("清空响应缓存: chainId={}, token={}", chainId, tokenAddress);
        return ResponseEntity.noContent().build();
    }

    /**
     * T3：触发当前 T2 评分上链
     * 流程：实时跑 T2 → 拿到含签名的响应 → OnchainOracleWriter.publishScore → 返回 tx_hash
     *
     * 前置：t3.enabled=true 且 health-oracle-address / publisher-private-key 已配置
     */
    @PostMapping("/projects/{chainId}/{tokenAddress}/publish")
    public ResponseEntity<Map<String, Object>> publishOnchain(
            HttpServletRequest httpReq,
            @PathVariable int chainId,
            @PathVariable String tokenAddress) {

        requireAdmin(httpReq);
        validateAddress(tokenAddress);

        if (!onchainOracleWriter.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "T3 未启用：set t3.enabled=true + 配置 t3.health-oracle-address / t3.publisher-private-key");
        }

        log.info("T3 触发上链: chainId={}, token={}", chainId, tokenAddress);
        T2HealthScoreResponse t2 = t2Service.computeAndSign(chainId, tokenAddress);
        String txHash;
        try {
            txHash = onchainOracleWriter.publish(t2);
        } catch (RuntimeException ex) {
            log.error("T3 上链失败: {}", ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "上链失败: " + ex.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "tx_hash", txHash,
                "score", t2.getScore(),
                "block_number", t2.getBlockNumber(),
                "signer", t2.getSignature().signerAddress()
        ));
    }

    /**
     * 强制重拉元数据：把 metadata_fetched_at 置 NULL，下次评分调用时 lazy-load 触发刷新
     */
    @PostMapping("/projects/{chainId}/{tokenAddress}/refresh")
    @Transactional
    @CacheEvict(value = HEALTH_SCORE_CACHE, allEntries = true)
    public ResponseEntity<Void> forceRefresh(
            HttpServletRequest httpReq,
            @PathVariable int chainId,
            @PathVariable String tokenAddress) {

        requireAdmin(httpReq);
        validateAddress(tokenAddress);

        String address = tokenAddress.toLowerCase();
        Project project = projectRepository.findByChainIdAndTokenAddress(chainId, address)
                .orElseThrow(() -> new ProjectNotFoundException(chainId, address));

        project.setMetadataFetchedAt(null);
        projectRepository.save(project);
        log.info("强制元数据刷新: chainId={}, token={}（下次评分调用触发）", chainId, address);
        return ResponseEntity.noContent().build();
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void requireAdmin(HttpServletRequest req) {
        if (!StringUtils.hasText(adminApiToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Admin 接口已禁用：请在 application.yml 或环境变量设置 admin.api-token");
        }
        String provided = req.getHeader("X-Admin-Token");
        if (!adminApiToken.equals(provided)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "X-Admin-Token 缺失或不匹配");
        }
    }

    private void validateAddress(String tokenAddress) {
        if (!EVM_ADDRESS.matcher(tokenAddress).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tokenAddress 必须为 0x 开头的 42 位 EVM 地址");
        }
    }

    /**
     * 应用单个字段覆盖
     * - null：不修改
     * - 非 null（含空串 ""）：写入并锁定字段
     */
    private void applyOverride(Project project, String fieldName, String newValue,
                                Consumer<String> setter) {
        if (newValue == null) return;
        setter.accept(newValue.isEmpty() ? null : newValue);
        project.lockField(fieldName);
    }

    /** 数值字段覆盖：null=不动，非 null=写入并锁定 */
    private void applyIntOverride(Project project, String fieldName, Integer newValue,
                                   Consumer<Integer> setter) {
        if (newValue == null) return;
        setter.accept(newValue);
        project.lockField(fieldName);
    }

    private ProjectIdentity buildIdentity(Project p) {
        String githubFullName = p.hasGithub()
                ? p.getGithubOwner() + "/" + p.getGithubRepo()
                : null;
        return ProjectIdentity.builder()
                .internalId(p.getId())
                .chainId(p.getChainId())
                .tokenAddress(p.getTokenAddress())
                .coingeckoId(p.getCoingeckoId())
                .defillamaId(p.getDefillamaId())
                .name(p.getName())
                .symbol(p.getSymbol())
                .slug(p.getSlug())
                .description(p.getDescription())
                .website(p.getWebsite())
                .logoUrl(p.getLogoUrl())
                .githubFullName(githubFullName)
                .twitterHandle(p.getTwitterHandle())
                .build();
    }
}
