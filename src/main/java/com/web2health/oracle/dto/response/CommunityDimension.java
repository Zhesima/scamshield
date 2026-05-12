package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommunityDimension {

    private int score;
    private double weight;

    // Widget API 唯一可靠数据：实时在线人数
    @JsonProperty("presence_count")
    private Integer presenceCount;

    private String source;
}
