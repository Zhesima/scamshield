package com.web2health.oracle.service;

import com.web2health.oracle.dto.collector.OnChainData;

/**
 * EIP-712 typed data 签名服务
 *
 * 用途：T2/T3 输出可链上验证的签名响应
 *
 * 实现选择：
 *   - LocalEcKeyPairSigner：本地私钥（dev / 单实例 prd）
 *   - TurnkeyMpcSigner：Turnkey MPC 钱包（推荐生产，避免单点泄露），后续接入
 *
 * 签名结构（EIP-712 typed data）：
 *   Domain：Web2HealthOracle / 1 / chainId / verifyingContract（T3 oracle 合约地址）
 *   Message HealthScore { chainId, tokenAddress, score, blockNumber, blockHash, computedAt }
 */
public interface SignerService {

    /**
     * 签 health score 数据
     *
     * @param chainId      链 ID
     * @param tokenAddress 代币合约地址（小写）
     * @param score        最终评分 [0, 100]
     * @param onChain      链上原始数据（含 block_number / block_hash）
     * @return 签名结果（含 signer 地址 + 签名值 + 域信息）
     */
    Signature sign(int chainId, String tokenAddress, int score, OnChainData onChain);

    /** 签名结果 */
    record Signature(
            String signerAddress,    // 0x-前缀 EVM 地址
            String value,            // 0x-前缀 65 字节 hex (r||s||v)
            String domain,           // "Web2HealthOracle"
            String version,          // "1"
            String scheme,           // "eip712"
            long signedAt            // unix timestamp
    ) {}
}
