package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * 评分元数据：本次"评分计算"的上下文
 *
 * 不放项目身份信息（移到 ProjectIdentity）
 * 只描述：什么时候算的、用了什么数据、是否缓存
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoreMetadata {

    /** 老接口兼容字段（= ProjectIdentity.internalId） */
    @Deprecated
    @JsonProperty("project_id")
    private Long projectId;

    @JsonProperty("collected_at")
    private Instant collectedAt;

    private boolean cached;

    @JsonProperty("cache_expires_at")
    private Instant cacheExpiresAt;

    /** 元数据上次从聚合器拉取的时间，T1 用于缓存控制 */
    @JsonProperty("metadata_fetched_at")
    private Instant metadataFetchedAt;

    /** 元数据来源：coingecko / defillama / manual */
    @JsonProperty("metadata_source")
    private String metadataSource;

    @JsonProperty("data_sources")
    private List<String> dataSources;

    @JsonProperty("missing_sources")
    private List<String> missingSources;
}
