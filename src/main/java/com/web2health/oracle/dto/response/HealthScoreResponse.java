package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web2health.oracle.domain.enums.Verdict;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 健康评分响应
 *
 * Tier 与信任级别（前置在响应顶层，让消费方一眼能区分）：
 *   T1 / soft  : 第三方聚合数据 + GitHub/Discord 软信号，仅参考
 *   T2 / hard  : 链上数据 + EIP-712 签名 + 块高复现，可信
 *   T3 / oracle: T2 + 上链 oracle 合约写入，链上 agent 可直读
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HealthScoreResponse {

    private int score;
    private Verdict verdict;

    /** "T1" / "T2" / "T3" */
    private String tier;

    /** "soft" / "hard" / "oracle" */
    @JsonProperty("trust_level")
    private String trustLevel;

    /** 项目身份信息（链上主键 + 展示元数据）*/
    private ProjectIdentity project;

    private Dimensions dimensions;

    @JsonProperty("risk_flags")
    private List<RiskFlag> riskFlags;

    private ScoreMetadata metadata;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Dimensions {

        @JsonProperty("dev_activity")
        private DevActivityDimension devActivity;

        /** Discord 实时在线人数维度 */
        private CommunityDimension community;

        /** Twitter / Telegram / Reddit 触达广度维度 */
        @JsonProperty("social_reach")
        private SocialReachDimension socialReach;

        // 待 Steam 接入实现
        @JsonProperty("game_traction")
        private Object gameTraction;
    }
}
