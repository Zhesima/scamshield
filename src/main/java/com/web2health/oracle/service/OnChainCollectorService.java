package com.web2health.oracle.service;

import com.web2health.oracle.dto.collector.OnChainData;

/**
 * T2 链上数据采集器
 *
 * 整合多个数据源（RPC + DexScreener）采集"硬指标"
 * 输出包含 block_number / block_hash 用于复现验证
 */
public interface OnChainCollectorService {

    OnChainData collect(int chainId, String tokenAddress);
}
