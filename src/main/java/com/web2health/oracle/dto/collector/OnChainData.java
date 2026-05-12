package com.web2health.oracle.dto.collector;

import lombok.Builder;
import lombok.Data;

import java.math.BigInteger;
import java.time.Instant;

/**
 * 链上采集结果（T2 维度数据源）
 *
 * 字段都是直接从链上 / DEX 聚合器拿来的"硬数据"，链上可复现验证
 *
 * blockNumber + blockHash 用于：
 *   1. 响应里 pinning 一个具体块高，让消费方能用同一块复现计算
 *   2. EIP-712 签名时把 block_hash 也签进去，防止旧数据被重放
 */
@Data
@Builder
public class OnChainData {

    /** 计算所基于的区块高度 */
    private long blockNumber;
    /** 该区块的 hash（防重放）*/
    private String blockHash;
    /** 区块时间戳 */
    private Instant blockTimestamp;

    // ── ERC-20 基础（来自 RPC eth_call）──
    private BigInteger totalSupply;
    private Integer decimals;

    /** 合约部署区块（用于计算合约年龄） */
    private Long contractCreationBlock;
    /** 合约年龄（天）*/
    private Integer contractAgeDays;

    // ── DEX 聚合数据（来自 DexScreener）──
    /** 全 DEX 累计流动性 USD */
    private Double liquidityUsd;
    /** 24h 总交易量 USD */
    private Double volume24hUsd;
    /** 当前价格 USD（取主交易对）*/
    private Double priceUsd;
    /** 流动性最高的交易对名称（如 ILV/WETH）*/
    private String primaryDexPair;
}
