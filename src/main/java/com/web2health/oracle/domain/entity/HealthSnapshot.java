package com.web2health.oracle.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "health_snapshots", indexes = {
        @Index(name = "idx_snapshot_project_id", columnList = "project_id"),
        @Index(name = "idx_snapshot_collected_at", columnList = "collected_at")
})
@Getter
@Setter
@NoArgsConstructor
public class HealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private Integer score;

    @Column(nullable = false, length = 20)
    private String verdict;

    // GitHub 采集字段
    @Column(name = "commits_30d")
    private Integer commits30d;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "commits_trend", columnDefinition = "json")
    private List<Integer> commitsTrend;

    @Column(name = "trend_direction", length = 20)
    private String trendDirection;

    @Column(name = "contributors_30d")
    private Integer contributors30d;

    @Column(name = "open_issues")
    private Integer openIssues;

    @Column(name = "last_commit_at")
    private LocalDateTime lastCommitAt;

    // Discord 采集字段
    @Column(name = "discord_member_count")
    private Integer discordMemberCount;

    @Column(name = "discord_presence_count")
    private Integer discordPresenceCount;

    @Column(name = "twitter_followers")
    private Integer twitterFollowers;

    @Column(name = "activity_ratio", columnDefinition = "DECIMAL(8,6)")
    private Double activityRatio;

    // 各维度得分
    @Column(name = "dev_activity_score")
    private Integer devActivityScore;

    @Column(name = "community_score")
    private Integer communityScore;

    @Column(name = "social_reach_score")
    private Integer socialReachScore;

    @Column(name = "game_traction_score")
    private Integer gameTractionScore;

    // 风险标记（JSON 存储）
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "risk_flags", columnDefinition = "json")
    private String riskFlagsJson;

    // 缺失数据源（逗号分隔）
    @Column(name = "missing_sources", length = 200)
    private String missingSources;

    @CreationTimestamp
    @Column(name = "collected_at", updatable = false)
    private LocalDateTime collectedAt;
}
