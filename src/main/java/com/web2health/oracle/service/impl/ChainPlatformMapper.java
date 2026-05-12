package com.web2health.oracle.service.impl;

import java.util.Map;
import java.util.Optional;

/**
 * EVM 链 ID 与 CoinGecko platform 字符串的映射
 *
 * CoinGecko 按合约反查时 URL 里要传 platform slug 而非 chainId
 * 例如 chain_id=1 → /coins/ethereum/contract/0x...
 *      chain_id=8453 → /coins/base/contract/0x...
 *
 * 完整列表见 CoinGecko：GET /asset_platforms
 * 这里仅维护主流 EVM 链，按需扩展
 */
final class ChainPlatformMapper {

    private static final Map<Integer, String> CHAIN_ID_TO_PLATFORM = Map.ofEntries(
            Map.entry(1, "ethereum"),
            Map.entry(10, "optimistic-ethereum"),
            Map.entry(56, "binance-smart-chain"),
            Map.entry(137, "polygon-pos"),
            Map.entry(8453, "base"),
            Map.entry(42161, "arbitrum-one"),
            Map.entry(43114, "avalanche"),
            Map.entry(250, "fantom"),
            Map.entry(100, "xdai"),
            Map.entry(534352, "scroll"),
            Map.entry(59144, "linea"),
            Map.entry(324, "zksync")
    );

    private ChainPlatformMapper() {}

    static Optional<String> toCoinGeckoPlatform(int chainId) {
        return Optional.ofNullable(CHAIN_ID_TO_PLATFORM.get(chainId));
    }
}
