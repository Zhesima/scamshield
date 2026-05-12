package com.web2health.oracle.service.impl;

import com.web2health.oracle.domain.entity.Project;
import com.web2health.oracle.domain.enums.TrendDirection;
import com.web2health.oracle.domain.enums.Verdict;
import com.web2health.oracle.dto.collector.DiscordData;
import com.web2health.oracle.dto.collector.GithubData;
import com.web2health.oracle.dto.response.*;
import com.web2health.oracle.dto.response.ProjectIdentity;
import com.web2health.oracle.service.HealthScoreEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class HealthScoreEngineImpl implements HealthScoreEngine {

    // 维度权重（合计 100%）
    // 维度缺失时按可用维度权重归一化（calcWeightedScore），避免因数据源不全导致评分虚低
    private static final double WEIGHT_DEV_ACTIVITY  = 0.35;
    private static final double WEIGHT_COMMUNITY     = 0.25;
    private static final double WEIGHT_SOCIAL_REACH  = 0.15;
    private static final double WEIGHT_GAME_TRACTION = 0.25;

    @Value("${cache.health-score-ttl:3600}")
    private long healthScoreTtlSeconds;

    @Override
    public HealthScoreResponse calculate(
            Project project,
            Optional<GithubData> githubData,
            Optional<DiscordData> discordData,
            boolean cached,
            Optional<Double> avgCommits30d) {

        List<String> missingSources = new ArrayList<>();
        List<String> dataSources = new ArrayList<>();
        List<RiskFlag> riskFlags = new ArrayList<>();

        // 计算 dev_activity 维度
        DevActivityDimension devActivityDimension = null;
        int devActivityScore = 0;
        if (githubData.isPresent()) {
            dataSources.add("github");
            devActivityDimension = buildDevActivityDimension(githubData.get(), riskFlags, avgCommits30d);
            devActivityScore = devActivityDimension.getScore();
        } else if (project.hasGithub()) {
            // GitHub 已配置但数据不可用（404 / 无权限 / 瞬时故障）
            missingSources.add("github");
        }

        // 计算 community 维度
        CommunityDimension communityDimension = null;
        int communityScore = 0;
        if (discordData.isPresent()) {
            dataSources.add("discord");
            communityDimension = buildCommunityDimension(discordData.get(), riskFlags);
            communityScore = communityDimension.getScore();
        } else if (project.hasDiscord()) {
            // Discord 已配置但数据不可用，未配置（guild_id = NULL）则不计为 missing
            missingSources.add("discord");
        }

        // 计算 social_reach 维度
        // 主指标 Twitter 粉丝（多数情况 null：CoinGecko 在 Twitter API 改革后无法获取此数据）
        // 备援指标 Telegram 用户数 / Reddit 订阅数（仍可用）
        SocialReachDimension socialReachDimension = null;
        Integer socialReachScore = null;
        if (hasAnySocialMetric(project)) {
            dataSources.add("social");
            socialReachDimension = buildSocialReachDimension(project, riskFlags);
            socialReachScore = socialReachDimension.getScore();
        }
        // 注意：social_reach 字段缺失不算 missing_source（聚合器没给就没给，无"配置"概念）

        // 权重归一化（当维度缺失时按实际可用维度计算）
        int totalScore = calcWeightedScore(
                githubData.isPresent() ? devActivityScore : null,
                discordData.isPresent() ? communityScore : null,
                socialReachScore
        );

        // 确定 verdict
        Verdict verdict = Verdict.fromScore(totalScore);

        // 任意 CRITICAL 级 flag 强制降为 danger
        boolean hasCritical = riskFlags.stream()
                .anyMatch(f -> f.getSeverity() == RiskFlag.Severity.CRITICAL);
        if (hasCritical) {
            verdict = Verdict.DANGER;
            totalScore = Math.min(totalScore, 24);
        }

        // 缺失数据源标记
        if (!missingSources.isEmpty()) {
            riskFlags.add(RiskFlag.builder()
                    .code("INCOMPLETE_DATA")
                    .severity(RiskFlag.Severity.LOW)
                    .description("部分数据源不可用: " + String.join(", ", missingSources))
                    .build());
        }

        Instant now = Instant.now();
        ScoreMetadata metadata = ScoreMetadata.builder()
                .projectId(project.getId())
                .collectedAt(now)
                .cached(cached)
                .cacheExpiresAt(now.plusSeconds(healthScoreTtlSeconds))
                .metadataFetchedAt(project.getMetadataFetchedAt() != null
                        ? project.getMetadataFetchedAt().toInstant(java.time.ZoneOffset.UTC) : null)
                .metadataSource(project.getMetadataSource())
                .dataSources(dataSources)
                .missingSources(missingSources)
                .build();

        return HealthScoreResponse.builder()
                .score(totalScore)
                .verdict(verdict)
                .tier("T1")
                .trustLevel("soft")
                .project(buildProjectIdentity(project))
                .dimensions(HealthScoreResponse.Dimensions.builder()
                        .devActivity(devActivityDimension)
                        .community(communityDimension)
                        .socialReach(socialReachDimension)
                        .gameTraction(null)
                        .build())
                .riskFlags(riskFlags)
                .metadata(metadata)
                .build();
    }

    private ProjectIdentity buildProjectIdentity(Project project) {
        String githubFullName = project.hasGithub()
                ? project.getGithubOwner() + "/" + project.getGithubRepo()
                : null;
        return ProjectIdentity.builder()
                .internalId(project.getId())
                .chainId(project.getChainId())
                .tokenAddress(project.getTokenAddress())
                .coingeckoId(project.getCoingeckoId())
                .defillamaId(project.getDefillamaId())
                .name(project.getName())
                .symbol(project.getSymbol())
                .slug(project.getSlug())
                .description(project.getDescription())
                .website(project.getWebsite())
                .logoUrl(project.getLogoUrl())
                .githubFullName(githubFullName)
                .twitterHandle(project.getTwitterHandle())
                .build();
    }

    private DevActivityDimension buildDevActivityDimension(
            GithubData github,
            List<RiskFlag> riskFlags,
            Optional<Double> avgCommits30d) {

        int score = calcDevActivityScore(github.getCommits30d(), github.getTrendDirection());

        // 风险标记：近30天无提交且历史均值>5
        if (github.getCommits30d() == 0 && avgCommits30d.map(avg -> avg > 5).orElse(false)) {
            riskFlags.add(RiskFlag.builder()
                    .code("LOW_DEV_ACTIVITY")
                    .severity(RiskFlag.Severity.MEDIUM)
                    .description("近30天无代码提交，但历史均值超过5次/月，疑似开发停滞")
                    .build());
        }

        return DevActivityDimension.builder()
                .score(score)
                .weight(WEIGHT_DEV_ACTIVITY)
                .commits30d(github.getCommits30d())
                .commitsTrend(github.getCommitsTrend())
                .trendDirection(github.getTrendDirection())
                .contributors30d(github.getContributors30d())
                .openIssues(github.getOpenIssues())
                .lastCommitAt(github.getLastCommitAt())
                .source("github")
                .build();
    }

    private int calcDevActivityScore(int commits30d, TrendDirection trendDirection) {
        int base;
        if (commits30d > 30) {
            base = 100;
        } else if (commits30d >= 10) {
            base = 70;
        } else if (commits30d >= 1) {
            base = 40;
        } else {
            base = 0;
        }

        if (trendDirection == TrendDirection.DECLINING) {
            base = Math.max(0, base - 10);
        }

        return base;
    }

    private CommunityDimension buildCommunityDimension(
            DiscordData discord,
            List<RiskFlag> riskFlags) {

        int presenceCount = discord.getPresenceCount();
        int score = calcCommunityScore(presenceCount);

        if (presenceCount < 50) {
            riskFlags.add(RiskFlag.builder()
                    .code("DISCORD_GHOST")
                    .severity(RiskFlag.Severity.MEDIUM)
                    .description("Discord 实时在线人数 " + presenceCount + " 低于 50，社区疑似空壳")
                    .build());
        }

        return CommunityDimension.builder()
                .score(score)
                .weight(WEIGHT_COMMUNITY)
                .presenceCount(presenceCount)
                .source("discord")
                .build();
    }

    /**
     * 用在线人数绝对值评分，适用于 Web3 游戏社区量级。
     * 绝对在线数比在线率更难造假（刷号可以提高总人数，但无法持续维持在线状态）。
     */
    private int calcCommunityScore(int presenceCount) {
        if (presenceCount > 2000) return 100;
        if (presenceCount >= 500)  return 70;
        if (presenceCount >= 50)   return 40;
        return 10;
    }

    /**
     * 社交触达维度评分
     *
     * 三个指标可用性：
     *   - Twitter followers：CoinGecko Twitter API 改革后已不返回，只有手工 override 能填
     *   - Telegram users：CoinGecko 仍提供，活跃 DeFi 项目通常 5k-50k
     *   - Reddit subscribers：CoinGecko 仍提供，但很多项目无 subreddit
     *
     * 优先级：Twitter > Telegram > Reddit；用第一个非 null 的指标按对应阈值评分
     */
    private SocialReachDimension buildSocialReachDimension(Project project, List<RiskFlag> riskFlags) {
        Integer twitter = nonZero(project.getTwitterFollowers());
        Integer telegram = nonZero(project.getTelegramUsers());
        Integer reddit = nonZero(project.getRedditSubscribers());

        int score;
        String dominant;
        if (twitter != null) {
            score = scoreByThreshold(twitter, 100_000, 10_000, 1_000);
            dominant = "twitter";
        } else if (telegram != null) {
            score = scoreByThreshold(telegram, 50_000, 5_000, 500);
            dominant = "telegram";
        } else if (reddit != null) {
            score = scoreByThreshold(reddit, 100_000, 10_000, 1_000);
            dominant = "reddit";
        } else {
            score = 0;
            dominant = "none";
        }

        // 风险标记：所有社交指标都很弱
        if (score <= 10 && dominant.equals("none")) {
            riskFlags.add(RiskFlag.builder()
                    .code("LOW_SOCIAL_REACH")
                    .severity(RiskFlag.Severity.LOW)
                    .description("无可用社交媒体数据，社区触达未知")
                    .build());
        }

        return SocialReachDimension.builder()
                .score(score)
                .weight(WEIGHT_SOCIAL_REACH)
                .twitterFollowers(twitter)
                .telegramUsers(telegram)
                .redditSubscribers(reddit)
                .source(project.getMetadataSource())
                .build();
    }

    /** Twitter / Telegram / Reddit 任一指标有值都算有数据 */
    private boolean hasAnySocialMetric(Project p) {
        return nonZero(p.getTwitterFollowers()) != null
                || nonZero(p.getTelegramUsers()) != null
                || nonZero(p.getRedditSubscribers()) != null;
    }

    private Integer nonZero(Integer v) {
        return v != null && v > 0 ? v : null;
    }

    /** 三档阈值评分：满分 / 中分 / 低分 / 兜底 10 分 */
    private int scoreByThreshold(int value, int high, int mid, int low) {
        if (value > high) return 100;
        if (value >= mid) return 70;
        if (value >= low) return 40;
        return 10;
    }

    /** 加权评分（缺失维度自动归一化）*/
    private int calcWeightedScore(Integer devScore, Integer communityScore, Integer socialScore) {
        double totalWeight = 0.0;
        double weightedSum = 0.0;

        if (devScore != null) {
            totalWeight += WEIGHT_DEV_ACTIVITY;
            weightedSum += devScore * WEIGHT_DEV_ACTIVITY;
        }
        if (communityScore != null) {
            totalWeight += WEIGHT_COMMUNITY;
            weightedSum += communityScore * WEIGHT_COMMUNITY;
        }
        if (socialScore != null) {
            totalWeight += WEIGHT_SOCIAL_REACH;
            weightedSum += socialScore * WEIGHT_SOCIAL_REACH;
        }

        if (totalWeight == 0.0) {
            return 0;
        }
        return (int) Math.round(weightedSum / totalWeight);
    }
}
