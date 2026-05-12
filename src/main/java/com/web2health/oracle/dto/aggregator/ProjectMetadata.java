package com.web2health.oracle.dto.aggregator;

import lombok.Builder;
import lombok.Data;

/**
 * 聚合器返回的项目元数据（统一规范化后）
 *
 * 凡是可空字段，调用方都需要做 null 检查
 * 设计原则：源头能拿到什么就给什么，不强制补全
 */
@Data
@Builder
public class ProjectMetadata {

    /** 元数据来源：coingecko / defillama / manual */
    private String source;

    // ── 身份标识 ──
    private String coingeckoId;
    private String defillamaId;

    // ── 展示信息 ──
    private String name;
    private String symbol;
    private String description;
    private String website;
    private String logoUrl;

    // ── 数据采集源（可空）──
    private String githubOwner;
    private String githubRepo;
    /** Discord guild ID（已解析），不是邀请码 */
    private String discordGuildId;
    private String twitterHandle;

    // ── 社交平台指标（CoinGecko community_data 提供，DefiLlama 部分提供）──
    /** Twitter 粉丝数 */
    private Integer twitterFollowers;
    /** Telegram 频道用户数 */
    private Integer telegramUsers;
    /** Reddit 订阅人数 */
    private Integer redditSubscribers;

    // ── 分类标签（DefiLlama category / CoinGecko categories[0]）──
    private String category;
}
