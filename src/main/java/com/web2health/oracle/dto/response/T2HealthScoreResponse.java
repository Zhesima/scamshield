package com.web2health.oracle.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.web2health.oracle.domain.enums.Verdict;
import com.web2health.oracle.service.SignerService;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * T2 Hard Score 响应（含 EIP-712 签名 + 块高 pinning）
 *
 * 与 T1 区别：
 *   1. tier="T2" / trust_level="hard"
 *   2. 顶层包含 block_number / block_hash / computed_at（pinning 信息）
 *   3. 单一 on_chain 维度（而不是软信号组合）
 *   4. 包含 signature 字段，消费方可 ecrecover 验证
 *
 * 验证示例（伪代码）：
 *   payload = "Web2HealthOracle\nchain={chainId}\ntoken={tokenAddress}\nscore={score}\n..."
 *   recovered = ecrecover(keccak256(eth_message_prefix + payload), signature)
 *   assert recovered == response.signature.signer
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class T2HealthScoreResponse {

    private int score;
    private Verdict verdict;

    /** "T2" */
    private String tier;

    /** "hard" */
    @JsonProperty("trust_level")
    private String trustLevel;

    /** 计算所基于的区块高度（消费方可用此块复现）*/
    @JsonProperty("block_number")
    private long blockNumber;

    @JsonProperty("block_hash")
    private String blockHash;

    @JsonProperty("computed_at")
    private Instant computedAt;

    private ProjectIdentity project;

    @JsonProperty("on_chain")
    private OnChainDimension onChain;

    /** EIP-712 签名（消费方 ecrecover 验证）*/
    private SignerService.Signature signature;
}
