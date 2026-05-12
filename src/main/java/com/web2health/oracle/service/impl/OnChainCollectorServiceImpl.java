package com.web2health.oracle.service.impl;

import com.web2health.oracle.dto.collector.OnChainData;
import com.web2health.oracle.service.OnChainCollectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;

/**
 * T2 链上数据采集实现
 *
 * 采集顺序：
 *   1. RPC 拿当前块（pinning 用）
 *   2. RPC 拿 totalSupply / decimals
 *   3. DexScreener 拿 liquidity / volume / price
 *
 * 任一步失败都抛 RuntimeException，T2 不做降级（链上是 hard 数据，缺数据就不签名）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OnChainCollectorServiceImpl implements OnChainCollectorService {

    private final Web3RpcClient rpcClient;
    private final DexScreenerClient dexScreenerClient;

    @Override
    public OnChainData collect(int chainId, String tokenAddress) {
        log.info("T2 链上数据采集开始: chainId={}, token={}", chainId, tokenAddress);

        // 1. 块高（pinning）
        Web3RpcClient.BlockInfo block = rpcClient.getLatestBlock();

        // 2. ERC-20 基础（必须成功，否则数据不可用）
        BigInteger totalSupply;
        int decimals;
        try {
            totalSupply = rpcClient.erc20TotalSupply(tokenAddress);
            decimals = rpcClient.erc20Decimals(tokenAddress);
        } catch (RuntimeException ex) {
            throw new RuntimeException("链上 ERC-20 调用失败（合约可能不存在或不是 ERC-20）: " + ex.getMessage(), ex);
        }

        // 3. DEX 聚合数据（允许为空：合约存在但没有 DEX 流动性）
        DexScreenerClient.DexAggregate dex = dexScreenerClient.fetchAggregate(chainId, tokenAddress);

        OnChainData data = OnChainData.builder()
                .blockNumber(block.number())
                .blockHash(block.hash())
                .blockTimestamp(Instant.ofEpochSecond(block.timestamp()))
                .totalSupply(totalSupply)
                .decimals(decimals)
                .liquidityUsd(dex.liquidityUsd())
                .volume24hUsd(dex.volume24hUsd())
                .priceUsd(dex.priceUsd())
                .primaryDexPair(dex.primaryPair())
                .build();

        log.info("T2 数据采集完成: block={}, supply={}, liquidity=${}, volume24h=${}",
                data.getBlockNumber(),
                totalSupply,
                String.format("%.0f", data.getLiquidityUsd()),
                String.format("%.0f", data.getVolume24hUsd()));
        return data;
    }
}
