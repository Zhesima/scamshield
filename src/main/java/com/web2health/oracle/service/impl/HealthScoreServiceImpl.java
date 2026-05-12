package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web2health.oracle.domain.entity.HealthSnapshot;
import com.web2health.oracle.domain.entity.Project;
import com.web2health.oracle.dto.aggregator.ProjectMetadata;
import com.web2health.oracle.dto.collector.DiscordData;
import com.web2health.oracle.dto.collector.GithubData;
import com.web2health.oracle.dto.response.HealthScoreResponse;
import com.web2health.oracle.exception.AllSourcesFailedException;
import com.web2health.oracle.exception.DataCollectionException;
import com.web2health.oracle.exception.ProjectNotFoundException;
import com.web2health.oracle.repository.HealthSnapshotRepository;
import com.web2health.oracle.repository.ProjectRepository;
import com.web2health.oracle.service.DiscordCollectorService;
import com.web2health.oracle.service.GithubCollectorService;
import com.web2health.oracle.service.HealthScoreEngine;
import com.web2health.oracle.service.HealthScoreService;
import com.web2health.oracle.service.MetadataAggregatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.web2health.oracle.config.CacheConfig.HEALTH_SCORE_CACHE;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthScoreServiceImpl implements HealthScoreService {

    private final ProjectRepository projectRepository;
    private final HealthSnapshotRepository snapshotRepository;
    private final GithubCollectorService githubCollectorService;
    private final DiscordCollectorService discordCollectorService;
    private final HealthScoreEngine healthScoreEngine;
    private final MetadataAggregatorService metadataAggregatorService;
    private final ObjectMapper objectMapper;

    /** 元数据缓存有效期（秒），超过则触发聚合器刷新；默认 24 小时 */
    @Value("${metadata.cache-ttl:86400}")
    private long metadataCacheTtlSeconds;

    // ── 老接口：按 ID / slug 查询（保留向后兼容）───────────────────────────────
    @Override
    @Cacheable(value = HEALTH_SCORE_CACHE, key = "#identifier", condition = "!#realtime")
    @Transactional
    public HealthScoreResponse getHealthScore(String identifier, boolean realtime) {
        Project project = resolveProject(identifier);
        return collectAndScore(project);
    }

    private Project resolveProject(String identifier) {
        try {
            long id = Long.parseLong(identifier);
            return projectRepository.findById(id)
                    .orElseThrow(() -> new ProjectNotFoundException(id));
        } catch (NumberFormatException e) {
            return projectRepository.findBySlug(identifier)
                    .orElseThrow(() -> new ProjectNotFoundException(identifier));
        }
    }

    // ── Web3 主接口：按 (chainId, tokenAddress) 查询，懒加载元数据 ─────────────
    @Override
    @Cacheable(
            value = HEALTH_SCORE_CACHE,
            key = "T(java.lang.String).format('%d:%s', #chainId, #tokenAddress.toLowerCase())",
            condition = "!#realtime"
    )
    @Transactional
    public HealthScoreResponse getHealthScoreByContract(int chainId, String tokenAddress, boolean realtime) {
        String normalized = tokenAddress.toLowerCase();
        Project project = projectRepository.findByChainIdAndTokenAddress(chainId, normalized)
                .map(p -> refreshMetadataIfStale(p, chainId, normalized))
                .orElseGet(() -> lazyLoadProject(chainId, normalized));
        return collectAndScore(project);
    }

    /** DB 未命中：调聚合器拉元数据 → 入库 → 返回 */
    private Project lazyLoadProject(int chainId, String tokenAddress) {
        log.info("懒加载触发: chainId={}, token={}", chainId, tokenAddress);
        ProjectMetadata meta = metadataAggregatorService.fetchByContract(chainId, tokenAddress)
                .orElseThrow(() -> new ProjectNotFoundException(chainId, tokenAddress));

        Project project = new Project();
        project.setChainId(chainId);
        project.setTokenAddress(tokenAddress);
        applyMetadata(project, meta);
        return projectRepository.save(project);
    }

    /** 元数据缓存过期则刷新，失败时降级使用旧元数据继续评分 */
    private Project refreshMetadataIfStale(Project project, int chainId, String tokenAddress) {
        LocalDateTime fetchedAt = project.getMetadataFetchedAt();
        boolean stale = fetchedAt == null
                || fetchedAt.isBefore(LocalDateTime.now().minusSeconds(metadataCacheTtlSeconds));
        if (!stale) {
            return project;
        }
        log.info("元数据过期，触发刷新: project={}, fetchedAt={}", project.getName(), fetchedAt);
        try {
            metadataAggregatorService.fetchByContract(chainId, tokenAddress).ifPresent(meta -> {
                applyMetadata(project, meta);
                projectRepository.save(project);
            });
        } catch (RuntimeException ex) {
            log.warn("元数据刷新失败，沿用旧值: project={}, reason={}", project.getName(), ex.getMessage());
        }
        return project;
    }

    /**
     * 把聚合器返回的元数据写到 Project 实体
     *
     * 写入规则：
     *   1. 字段在 manual_overrides 列表里 → 跳过（人工锁定不覆盖）
     *   2. 当前字段为空 + 新值非空 → 写入
     *   3. 当前字段非空 → 不覆盖（保留首次/人工值，避免聚合器抽风改名时数据动荡）
     *
     * 例外：name 为空时强制兜底，避免主键身份缺失
     */
    private void applyMetadata(Project project, ProjectMetadata meta) {
        // name 兜底：如果当前完全没值且聚合器也没给，至少保证 NOT NULL
        if ((project.getName() == null || project.getName().isBlank()) && !project.isFieldLocked("name")) {
            project.setName(meta.getName() != null ? meta.getName() : "Unknown");
        }

        applyIfNotLocked(project, "symbol", project::getSymbol, project::setSymbol, meta.getSymbol());
        applyIfNotLocked(project, "description", project::getDescription, project::setDescription, meta.getDescription());
        applyIfNotLocked(project, "website", project::getWebsite, project::setWebsite, meta.getWebsite());
        applyIfNotLocked(project, "logoUrl", project::getLogoUrl, project::setLogoUrl, meta.getLogoUrl());
        applyIfNotLocked(project, "coingeckoId", project::getCoingeckoId, project::setCoingeckoId, meta.getCoingeckoId());
        applyIfNotLocked(project, "defillamaId", project::getDefillamaId, project::setDefillamaId, meta.getDefillamaId());
        applyIfNotLocked(project, "githubOwner", project::getGithubOwner, project::setGithubOwner, meta.getGithubOwner());
        applyIfNotLocked(project, "githubRepo", project::getGithubRepo, project::setGithubRepo, meta.getGithubRepo());
        applyIfNotLocked(project, "discordGuildId", project::getDiscordGuildId, project::setDiscordGuildId, meta.getDiscordGuildId());
        applyIfNotLocked(project, "twitterHandle", project::getTwitterHandle, project::setTwitterHandle, meta.getTwitterHandle());
        applyIfNotLocked(project, "category", project::getCategory, project::setCategory, meta.getCategory());

        // 数值字段：聚合器的最新数据可信度高于"曾经写过的"老值，定期刷新更合理
        // 但仍尊重 manual_overrides（运维人员锁定后不动）
        applyIntIfNotLocked(project, "twitterFollowers", project::setTwitterFollowers, meta.getTwitterFollowers());
        applyIntIfNotLocked(project, "telegramUsers", project::setTelegramUsers, meta.getTelegramUsers());
        applyIntIfNotLocked(project, "redditSubscribers", project::setRedditSubscribers, meta.getRedditSubscribers());

        // slug 派生：若没人工设置，用 coingecko_id 兜底
        if ((project.getSlug() == null || project.getSlug().isBlank())
                && !project.isFieldLocked("slug")
                && meta.getCoingeckoId() != null) {
            project.setSlug(meta.getCoingeckoId());
        }

        // 如果人工已设过任意字段，metadata_source 保持 manual（不被覆盖）
        boolean hasManualLock = project.getManualOverrides() != null && !project.getManualOverrides().isEmpty();
        if (!hasManualLock) {
            project.setMetadataSource(meta.getSource());
        }
        project.setMetadataFetchedAt(LocalDateTime.now());
    }

    /** 字段未被锁定 + 当前为空 + 新值非空 → 写入 */
    private void applyIfNotLocked(Project project, String fieldName,
                                  java.util.function.Supplier<String> getter,
                                  java.util.function.Consumer<String> setter,
                                  String newValue) {
        if (project.isFieldLocked(fieldName)) return;
        String current = getter.get();
        if ((current == null || current.isBlank()) && newValue != null && !newValue.isBlank()) {
            setter.accept(newValue);
        }
    }

    /**
     * 数值字段刷新：未锁定 + 新值非空 → 写入（覆盖旧值）
     * 与 String 字段不同：粉丝数等指标本身就在变，每次刷新应取最新值
     */
    private void applyIntIfNotLocked(Project project, String fieldName,
                                     java.util.function.Consumer<Integer> setter,
                                     Integer newValue) {
        if (project.isFieldLocked(fieldName)) return;
        if (newValue != null) {
            setter.accept(newValue);
        }
    }

    // ── 共享流程：采集 + 评分 + 落快照 ─────────────────────────────────────────
    private HealthScoreResponse collectAndScore(Project project) {
        Optional<Double> avgCommits30d = snapshotRepository.findAvgCommits30dByProjectId(project.getId());

        Optional<GithubData> githubData = Optional.empty();
        Optional<DiscordData> discordData = Optional.empty();
        // 404 / 无权限 = 仓库配置错误，不计入"配置了但瞬时故障"的计数
        boolean githubUnavailable = false;

        if (project.hasGithub()) {
            try {
                githubData = Optional.of(
                        githubCollectorService.collect(project.getGithubOwner(), project.getGithubRepo()));
            } catch (DataCollectionException ex) {
                if (isRepoNotFound(ex)) {
                    log.warn("GitHub 仓库不存在或无权限，跳过 GitHub 维度: project={}, repo={}/{}",
                            project.getName(), project.getGithubOwner(), project.getGithubRepo());
                    githubUnavailable = true;
                } else {
                    log.warn("GitHub 采集失败，降级处理: project={}, reason={}", project.getName(), ex.getMessage());
                }
            }
        }

        if (project.hasDiscord()) {
            try {
                discordData = Optional.of(
                        discordCollectorService.collect(project.getDiscordGuildId()));
            } catch (DataCollectionException ex) {
                log.warn("Discord 采集失败，降级处理: project={}, reason={}", project.getName(), ex.getMessage());
            }
        }

        // 仅当"配置了且可访问的数据源"全部瞬时故障时才返回 500
        // 404 视为配置错误（仓库不存在/无权限），不参与此判断
        boolean githubEffective = project.hasGithub() && !githubUnavailable;
        boolean hasAnyConfiguredSource = githubEffective || project.hasDiscord();
        boolean anySuccess = githubData.isPresent() || discordData.isPresent();
        if (hasAnyConfiguredSource && !anySuccess) {
            throw new AllSourcesFailedException("项目 " + project.getName() + " 所有数据源采集失败");
        }

        HealthScoreResponse response = healthScoreEngine.calculate(
                project, githubData, discordData, false, avgCommits30d);

        saveSnapshot(project, githubData, discordData, response);

        return response;
    }

    private void saveSnapshot(Project project,
                              Optional<GithubData> githubData,
                              Optional<DiscordData> discordData,
                              HealthScoreResponse response) {
        try {
            HealthSnapshot snapshot = new HealthSnapshot();
            snapshot.setProject(project);
            snapshot.setScore(response.getScore());
            snapshot.setVerdict(response.getVerdict().getValue());

            githubData.ifPresent(g -> {
                snapshot.setCommits30d(g.getCommits30d());
                snapshot.setCommitsTrend(g.getCommitsTrend());
                snapshot.setTrendDirection(g.getTrendDirection() != null
                        ? g.getTrendDirection().getValue() : null);
                snapshot.setContributors30d(g.getContributors30d());
                snapshot.setOpenIssues(g.getOpenIssues());
                if (g.getLastCommitAt() != null) {
                    snapshot.setLastCommitAt(g.getLastCommitAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
                }
            });

            discordData.ifPresent(d -> snapshot.setDiscordPresenceCount(d.getPresenceCount()));
            snapshot.setTwitterFollowers(project.getTwitterFollowers());

            if (response.getDimensions().getDevActivity() != null) {
                snapshot.setDevActivityScore(response.getDimensions().getDevActivity().getScore());
            }
            if (response.getDimensions().getCommunity() != null) {
                snapshot.setCommunityScore(response.getDimensions().getCommunity().getScore());
            }
            if (response.getDimensions().getSocialReach() != null) {
                snapshot.setSocialReachScore(response.getDimensions().getSocialReach().getScore());
            }

            snapshot.setRiskFlagsJson(objectMapper.writeValueAsString(response.getRiskFlags()));
            snapshot.setMissingSources(String.join(",", response.getMetadata().getMissingSources()));

            snapshotRepository.save(snapshot);
        } catch (JsonProcessingException ex) {
            log.error("保存快照失败，序列化 risk_flags 出错", ex);
        } catch (Exception ex) {
            log.error("保存健康评分快照失败: projectId={}", project.getId(), ex);
        }
    }

    /** 判断采集异常是否源于仓库不存在（HTTP 404）*/
    private boolean isRepoNotFound(DataCollectionException ex) {
        return ex.getCause() instanceof WebClientResponseException wce
                && wce.getStatusCode() == HttpStatus.NOT_FOUND;
    }
}
