package com.web2health.oracle.service;

import com.web2health.oracle.dto.response.HealthScoreResponse;

public interface HealthScoreService {

    /**
     * 老接口：按 ID / slug 查询健康评分
     *
     * @param identifier 项目 ID（数字）或 slug（如 "illuvium"）
     * @param realtime   true=强制实时采集跳过缓存
     */
    HealthScoreResponse getHealthScore(String identifier, boolean realtime);

    /**
     * Web3 主接口：按 (chainId, tokenAddress) 查询健康评分
     *
     * 数据库未命中时调用聚合器（CoinGecko）按需懒加载元数据后入库再评分
     *
     * @param chainId      EVM 链 ID（1=Ethereum, 8453=Base, ...）
     * @param tokenAddress ERC-20 合约地址（自动转小写）
     * @param realtime     true=强制实时采集跳过缓存
     */
    HealthScoreResponse getHealthScoreByContract(int chainId, String tokenAddress, boolean realtime);
}
