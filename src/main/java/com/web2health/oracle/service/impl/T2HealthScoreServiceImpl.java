package com.web2health.oracle.service.impl;

import com.web2health.oracle.domain.entity.Project;
import com.web2health.oracle.domain.enums.Verdict;
import com.web2health.oracle.dto.collector.OnChainData;
import com.web2health.oracle.dto.response.OnChainDimension;
import com.web2health.oracle.dto.response.ProjectIdentity;
import com.web2health.oracle.dto.response.T2HealthScoreResponse;
import com.web2health.oracle.repository.ProjectRepository;
import com.web2health.oracle.service.OnChainCollectorService;
import com.web2health.oracle.service.SignerService;
import com.web2health.oracle.service.T2HealthScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * T2 Hard Score 实现
 *
 * 评分公式（链上 hard 指标，三档阈值）：
 *   1. liquidity_usd 权重 50%（流动性深度 = 退出能力）
 *      > $1M:   100
 *      $100k-1M: 70
 *      $10k-100k: 40
 *      < $10k:  10
 *   2. volume_24h_usd 权重 30%（活跃度）
 *      > $500k:  100
 *      $50k-500k: 70
 *      $5k-50k:   40
 *      < $5k:    10
 *   3. supply_present 权重 20%（合约存在 + 有 totalSupply 即满分；ERC-20 调用失败上面已抛异常）
 *
 * 简化版三指标够 T2 演示。后续可加：
 *   - holders 数（需 Alchemy / Etherscan Pro）
 *   - 合约年龄
 *   - 审计状态（CertiK / GoPlus 接入）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class T2HealthScoreServiceImpl implements T2HealthScoreService {

    private final OnChainCollectorService onChainCollectorService;
    private final SignerService signerService;
    private final ProjectRepository projectRepository;

    private static final double WEIGHT_LIQUIDITY = 0.50;
    private static final double WEIGHT_VOLUME    = 0.30;
    private static final double WEIGHT_SUPPLY    = 0.20;

    @Override
    public T2HealthScoreResponse computeAndSign(int chainId, String tokenAddress) {
        String addr = tokenAddress.toLowerCase();
        log.info("T2 计算开始: chainId={}, token={}", chainId, addr);

        // 1. 链上数据
        OnChainData onChain = onChainCollectorService.collect(chainId, addr);

        // 2. 计算评分
        int liquidityScore = scoreByThreshold(
                onChain.getLiquidityUsd() == null ? 0 : onChain.getLiquidityUsd(),
                1_000_000, 100_000, 10_000);
        int volumeScore = scoreByThreshold(
                onChain.getVolume24hUsd() == null ? 0 : onChain.getVolume24hUsd(),
                500_000, 50_000, 5_000);
        int supplyScore = onChain.getTotalSupply() != null ? 100 : 0;

        double weighted = liquidityScore * WEIGHT_LIQUIDITY
                        + volumeScore    * WEIGHT_VOLUME
                        + supplyScore    * WEIGHT_SUPPLY;
        int finalScore = (int) Math.round(weighted);
        Verdict verdict = Verdict.fromScore(finalScore);

        log.info("T2 评分细节: liquidity={}({}%), volume={}({}%), supply={}({}%) → final={}",
                liquidityScore, (int)(WEIGHT_LIQUIDITY*100),
                volumeScore,   (int)(WEIGHT_VOLUME*100),
                supplyScore,   (int)(WEIGHT_SUPPLY*100),
                finalScore);

        // 3. EIP-712 签名
        SignerService.Signature signature = signerService.sign(chainId, addr, finalScore, onChain);

        // 4. 拉项目身份信息（如果 T1 已经入库就用，否则简化身份）
        ProjectIdentity identity = buildProjectIdentity(chainId, addr);

        // 5. 拼装响应
        OnChainDimension dim = OnChainDimension.builder()
                .score(finalScore)  // 维度内分数 = 总分（T2 单一维度）
                .weight(1.0)
                .liquidityUsd(onChain.getLiquidityUsd())
                .volume24hUsd(onChain.getVolume24hUsd())
                .priceUsd(onChain.getPriceUsd())
                .primaryDexPair(onChain.getPrimaryDexPair())
                .totalSupply(onChain.getTotalSupply() != null ? onChain.getTotalSupply().toString() : null)
                .decimals(onChain.getDecimals())
                .source("rpc+dexscreener")
                .build();

        return T2HealthScoreResponse.builder()
                .score(finalScore)
                .verdict(verdict)
                .tier("T2")
                .trustLevel("hard")
                .blockNumber(onChain.getBlockNumber())
                .blockHash(onChain.getBlockHash())
                .computedAt(Instant.ofEpochSecond(signature.signedAt()))
                .project(identity)
                .onChain(dim)
                .signature(signature)
                .build();
    }

    /** 三档阈值评分 */
    private int scoreByThreshold(double value, double high, double mid, double low) {
        if (value > high) return 100;
        if (value >= mid) return 70;
        if (value >= low) return 40;
        return 10;
    }

    /** 复用 T1 已经懒加载入库的项目身份；没有则只填 chainId+address */
    private ProjectIdentity buildProjectIdentity(int chainId, String addr) {
        Project p = projectRepository.findByChainIdAndTokenAddress(chainId, addr).orElse(null);
        if (p == null) {
            return ProjectIdentity.builder()
                    .chainId(chainId)
                    .tokenAddress(addr)
                    .build();
        }
        String github = p.hasGithub() ? p.getGithubOwner() + "/" + p.getGithubRepo() : null;
        return ProjectIdentity.builder()
                .internalId(p.getId())
                .chainId(p.getChainId())
                .tokenAddress(p.getTokenAddress())
                .coingeckoId(p.getCoingeckoId())
                .name(p.getName())
                .symbol(p.getSymbol())
                .slug(p.getSlug())
                .website(p.getWebsite())
                .logoUrl(p.getLogoUrl())
                .githubFullName(github)
                .twitterHandle(p.getTwitterHandle())
                .build();
    }
}
