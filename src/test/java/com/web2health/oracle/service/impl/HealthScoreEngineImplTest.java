package com.web2health.oracle.service.impl;

import com.web2health.oracle.domain.entity.Project;
import com.web2health.oracle.domain.enums.TrendDirection;
import com.web2health.oracle.domain.enums.Verdict;
import com.web2health.oracle.dto.collector.DiscordData;
import com.web2health.oracle.dto.collector.GithubData;
import com.web2health.oracle.dto.response.HealthScoreResponse;
import com.web2health.oracle.dto.response.RiskFlag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("HealthScoreEngineImpl — 评分引擎核心逻辑")
class HealthScoreEngineImplTest {

    private HealthScoreEngineImpl engine;

    @BeforeEach
    void init() {
        engine = new HealthScoreEngineImpl();
        ReflectionTestUtils.setField(engine, "healthScoreTtlSeconds", 3600L);
    }

    private Project basicProject() {
        Project p = new Project();
        p.setId(1L);
        p.setChainId(1);
        p.setTokenAddress("0x" + "ab".repeat(20));
        p.setName("Test");
        p.setSymbol("TST");
        return p;
    }

    private GithubData github(int commits30d, TrendDirection dir) {
        return GithubData.builder()
                .commits30d(commits30d)
                .commitsTrend(List.of(commits30d / 4, commits30d / 4, commits30d / 4, commits30d / 4))
                .trendDirection(dir)
                .contributors30d(3)
                .openIssues(10)
                .build();
    }

    @Nested
    @DisplayName("单维度评分")
    class SingleDimension {

        @Test
        @DisplayName("dev_activity > 30 commits → 100；归一化后总分 = 100")
        void devActivity_high() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.of(github(50, TrendDirection.RISING)),
                    Optional.empty(), false, Optional.empty());
            assertEquals(100, r.getDimensions().getDevActivity().getScore());
            assertEquals(100, r.getScore(), "仅 github 一个维度 → 归一化后总分 = 维度分");
            assertEquals(Verdict.HEALTHY, r.getVerdict());
        }

        @Test
        @DisplayName("dev_activity 0 commits → 0；trend declining 仍是 0（已无 negative）")
        void devActivity_dead() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.of(github(0, TrendDirection.DEAD)),
                    Optional.empty(), false, Optional.empty());
            assertEquals(0, r.getDimensions().getDevActivity().getScore());
            assertEquals(0, r.getScore());
            assertEquals(Verdict.DANGER, r.getVerdict());
        }

        @Test
        @DisplayName("dev_activity 中等 + declining → 70 - 10 = 60")
        void devActivity_decliningPenalty() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.of(github(15, TrendDirection.DECLINING)),
                    Optional.empty(), false, Optional.empty());
            assertEquals(60, r.getDimensions().getDevActivity().getScore());
        }

        @Test
        @DisplayName("community presence > 2000 → 100")
        void community_full() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(),
                    Optional.of(DiscordData.builder().presenceCount(5000).build()),
                    false, Optional.empty());
            assertEquals(100, r.getDimensions().getCommunity().getScore());
            assertEquals(100, r.getScore());
        }

        @Test
        @DisplayName("social_reach 优先 Twitter > Telegram > Reddit；都没 → 维度不出现")
        void socialReach_priority() {
            Project p = basicProject();
            p.setTwitterFollowers(150_000);
            p.setTelegramUsers(100);  // 应被忽略，因为 Twitter 优先
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(), Optional.empty(),
                    false, Optional.empty());
            assertNotNull(r.getDimensions().getSocialReach());
            assertEquals(100, r.getDimensions().getSocialReach().getScore());
        }

        @Test
        @DisplayName("social_reach 仅 Telegram → 用 Telegram 阈值评分")
        void socialReach_telegramFallback() {
            Project p = basicProject();
            p.setTelegramUsers(8000);  // 5k-50k → 70
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(), Optional.empty(),
                    false, Optional.empty());
            assertEquals(70, r.getDimensions().getSocialReach().getScore());
        }

        @Test
        @DisplayName("没有任何数据源 → score=0, danger, dimensions 全为空")
        void noData() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(), Optional.empty(),
                    false, Optional.empty());
            assertEquals(0, r.getScore());
            assertEquals(Verdict.DANGER, r.getVerdict());
            assertNull(r.getDimensions().getDevActivity());
            assertNull(r.getDimensions().getCommunity());
            assertNull(r.getDimensions().getSocialReach());
        }
    }

    @Nested
    @DisplayName("权重归一化")
    class WeightNormalization {

        @Test
        @DisplayName("github(100) + discord(40) → (100×0.35 + 40×0.25) / 0.60 = 75")
        void twoDimensionsNormalized() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.of(github(50, TrendDirection.RISING)),
                    Optional.of(DiscordData.builder().presenceCount(100).build()),  // 50-500 → 40
                    false, Optional.empty());
            // dev=100, community=40; 加权 (100*0.35 + 40*0.25) = 35 + 10 = 45; 归一化 45/0.60 = 75
            assertEquals(75, r.getScore());
        }

        @Test
        @DisplayName("三维度全在 → 按 35/25/15 加权归一化")
        void threeDimensions() {
            Project p = basicProject();
            p.setTwitterFollowers(500_000);  // social = 100
            HealthScoreResponse r = engine.calculate(
                    p, Optional.of(github(50, TrendDirection.RISING)),
                    Optional.of(DiscordData.builder().presenceCount(5000).build()),
                    false, Optional.empty());
            // 100*0.35 + 100*0.25 + 100*0.15 = 75; 归一化 75/0.75 = 100
            assertEquals(100, r.getScore());
        }
    }

    @Nested
    @DisplayName("Risk flags 与 verdict")
    class RiskFlags {

        @Test
        @DisplayName("commits 0 + 历史平均 > 5 → LOW_DEV_ACTIVITY medium")
        void lowDevActivityFlag() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.of(github(0, TrendDirection.DEAD)),
                    Optional.empty(), false, Optional.of(8.0));
            assertTrue(r.getRiskFlags().stream()
                    .anyMatch(f -> "LOW_DEV_ACTIVITY".equals(f.getCode())));
        }

        @Test
        @DisplayName("Discord presence < 50 → DISCORD_GHOST medium")
        void discordGhostFlag() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(),
                    Optional.of(DiscordData.builder().presenceCount(20).build()),
                    false, Optional.empty());
            assertTrue(r.getRiskFlags().stream()
                    .anyMatch(f -> "DISCORD_GHOST".equals(f.getCode())));
        }

        @Test
        @DisplayName("已配置 GitHub 但数据不可用 → INCOMPLETE_DATA + missing_sources 含 github")
        void incompleteDataFlag() {
            Project p = basicProject();
            p.setGithubOwner("foo");
            p.setGithubRepo("bar");
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(), Optional.empty(),
                    false, Optional.empty());
            assertTrue(r.getMetadata().getMissingSources().contains("github"));
            assertTrue(r.getRiskFlags().stream()
                    .anyMatch(f -> "INCOMPLETE_DATA".equals(f.getCode())));
        }

        @Test
        @DisplayName("未配置 Discord（guild_id 为 NULL）→ 不出现在 missing_sources")
        void unconfiguredDiscordNotMissing() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(), Optional.empty(),
                    false, Optional.empty());
            assertFalse(r.getMetadata().getMissingSources().contains("discord"));
        }
    }

    @Nested
    @DisplayName("Tier 与身份")
    class TierIdentity {

        @Test
        @DisplayName("引擎输出 tier=T1 / trust_level=soft / project 含链上身份")
        void tierMetadata() {
            Project p = basicProject();
            HealthScoreResponse r = engine.calculate(
                    p, Optional.empty(), Optional.empty(),
                    false, Optional.empty());
            assertEquals("T1", r.getTier());
            assertEquals("soft", r.getTrustLevel());
            assertEquals(1, r.getProject().getChainId());
            assertEquals(p.getTokenAddress(), r.getProject().getTokenAddress());
        }
    }
}
