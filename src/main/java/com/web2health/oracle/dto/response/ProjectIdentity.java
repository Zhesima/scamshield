package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * 项目身份信息
 *
 * 暴露给消费方用于：
 *   1) 验证调用的确实是预期项目（防止 slug 模糊匹配错配）
 *   2) UI 展示（name, symbol, logo, website）
 *   3) 跨系统对账（chain_id + token_address 是链上唯一标识）
 *
 * 设计原则：所有字段都是 nullable，没拉到就不返回（@JsonInclude(NON_NULL)）
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProjectIdentity {

    /** 内部数据库 ID，跨实例不稳定，仅用于本服务内部追踪 */
    @JsonProperty("internal_id")
    private Long internalId;

    // ── Web3 主键（链上唯一）──
    @JsonProperty("chain_id")
    private Integer chainId;

    @JsonProperty("token_address")
    private String tokenAddress;

    // ── 第三方权威 ID ──
    @JsonProperty("coingecko_id")
    private String coingeckoId;

    @JsonProperty("defillama_id")
    private String defillamaId;

    // ── 展示信息 ──
    private String name;
    private String symbol;
    private String slug;
    private String description;
    private String website;

    @JsonProperty("logo_url")
    private String logoUrl;

    // ── 数据源 handle（消费方可选地用作交叉验证）──
    @JsonProperty("github")
    private String githubFullName;     // owner/repo

    @JsonProperty("twitter_handle")
    private String twitterHandle;
}
