package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web2health.oracle.domain.enums.TrendDirection;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class DevActivityDimension {

    private int score;
    private double weight;

    @JsonProperty("commits_30d")
    private Integer commits30d;

    @JsonProperty("commits_trend")
    private List<Integer> commitsTrend;

    @JsonProperty("trend_direction")
    private TrendDirection trendDirection;

    @JsonProperty("contributors_30d")
    private Integer contributors30d;

    @JsonProperty("open_issues")
    private Integer openIssues;

    @JsonProperty("last_commit_at")
    private Instant lastCommitAt;

    private String source;
}
