package com.web2health.oracle.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Web3 项目元数据实体（T1 起退化为缓存层）
 *
 * 主键身份：(chain_id, token_address) — 链上唯一，不可改
 * 老的 id / slug 仅用于内部引用和兼容老接口
 *
 * 数据来源：调用聚合器（CoinGecko / DefiLlama）按需懒加载入库
 * 缓存策略：metadata_fetched_at 超过 TTL 触发重新拉取
 */
@Entity
@Table(name = "projects",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_projects_chain_token",
                columnNames = {"chain_id", "token_address"}
        ),
        indexes = {
                @Index(name = "idx_projects_coingecko", columnList = "coingecko_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Web3 主键身份 ──────────────────────────────────────────────────────────
    /** EVM 链 ID：1=Ethereum, 8453=Base, 137=Polygon, 42161=Arbitrum, 10=Optimism */
    @Column(name = "chain_id")
    private Integer chainId;

    /** ERC-20 合约地址，统一小写存储 */
    @Column(name = "token_address", length = 66)
    private String tokenAddress;

    // ── 第三方权威 ID ─────────────────────────────────────────────────────────
    /** CoinGecko 项目 ID（如 illuvium），用于元数据反查 */
    @Column(name = "coingecko_id", length = 64)
    private String coingeckoId;

    /** DefiLlama 协议 slug，元数据备援源 */
    @Column(name = "defillama_id", length = 128)
    private String defillamaId;

    // ── 展示元数据 ────────────────────────────────────────────────────────────
    @Column(nullable = false, length = 255)
    private String name;

    /** Token 符号，如 ILV / AXS */
    @Column(length = 32)
    private String symbol;

    /** URL 友好标识，老接口兼容用，懒加载时可由 coingecko_id 派生 */
    @Column(length = 64)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String website;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    // ── 数据采集源（来自聚合器或人工补全）─────────────────────────────────────
    @Column(name = "github_owner", length = 64)
    private String githubOwner;

    @Column(name = "github_repo", length = 128)
    private String githubRepo;

    @Column(name = "discord_guild_id", length = 32)
    private String discordGuildId;

    /** Twitter handle，不带 @ */
    @Column(name = "twitter_handle", length = 64)
    private String twitterHandle;

    /** Twitter 粉丝数（来自 CoinGecko community_data） */
    @Column(name = "twitter_followers")
    private Integer twitterFollowers;

    /** Telegram 频道用户数 */
    @Column(name = "telegram_users")
    private Integer telegramUsers;

    /** Reddit 订阅人数 */
    @Column(name = "reddit_subscribers")
    private Integer redditSubscribers;

    /** 项目分类（DefiLlama category / CoinGecko categories[0]）*/
    @Column(length = 64)
    private String category;

    /** Phase 2 Steam 维度用，T1 不消费 */
    @Column(name = "steam_app_id", length = 20)
    private String steamAppId;

    // ── 缓存控制 ─────────────────────────────────────────────────────────────
    /** 元数据上次刷新时间，超过 TTL 触发聚合器重新拉取 */
    @Column(name = "metadata_fetched_at")
    private LocalDateTime metadataFetchedAt;

    /** 元数据来源：coingecko / defillama / manual */
    @Column(name = "metadata_source", length = 32)
    private String metadataSource;

    /** 已被人工覆盖的字段名列表（如 ["githubRepo","discordGuildId"]），auto-refresh 时跳过 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manual_overrides", columnDefinition = "json")
    private List<String> manualOverrides;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── 业务判断方法 ──────────────────────────────────────────────────────────
    public boolean hasGithub() {
        return githubOwner != null && !githubOwner.isBlank()
                && githubRepo != null && !githubRepo.isBlank();
    }

    public boolean hasDiscord() {
        return discordGuildId != null && !discordGuildId.isBlank();
    }

    public boolean hasOnchainIdentity() {
        return chainId != null && tokenAddress != null && !tokenAddress.isBlank();
    }

    // ── 字段锁定（人工覆盖）─────────────────────────────────────────────────
    public boolean isFieldLocked(String fieldName) {
        return manualOverrides != null && manualOverrides.contains(fieldName);
    }

    public void lockField(String fieldName) {
        if (manualOverrides == null) {
            manualOverrides = new ArrayList<>();
        }
        if (!manualOverrides.contains(fieldName)) {
            manualOverrides.add(fieldName);
        }
    }

    public void unlockField(String fieldName) {
        if (manualOverrides != null) {
            manualOverrides.remove(fieldName);
        }
    }
}
