package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * T2 链上维度
 *
 * 不同于 T1 软维度，这里所有数值都来自链上 RPC / DEX 数据
 * 消费方可拿 block_number 自己复现验证
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnChainDimension {

    private int score;
    private double weight;

    @JsonProperty("liquidity_usd")
    private Double liquidityUsd;

    @JsonProperty("volume_24h_usd")
    private Double volume24hUsd;

    @JsonProperty("price_usd")
    private Double priceUsd;

    @JsonProperty("primary_dex_pair")
    private String primaryDexPair;

    @JsonProperty("total_supply")
    private String totalSupply;

    private Integer decimals;

    /** "rpc+dexscreener" 或更细分 */
    private String source;
}
