package com.web2health.oracle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * T3 上链 oracle 配置
 *
 * 启用条件：t3.enabled=true 且 health-oracle-address 已配置
 *
 * publisher-private-key 是发起广播交易的账户私钥（付 gas）
 *   推荐：与 oracle signer 私钥分开（一个签数据 / 一个广播交易 / 后续可分多个 publisher 实现冗余）
 *   开发：可以与 signer 复用同一个 EOA
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "t3")
public class T3Properties {

    private boolean enabled = false;

    /** HealthOracle 合约地址（Foundry 部署后填）*/
    private String healthOracleAddress = "";

    /** 链 ID（Base Sepolia=84532, Base mainnet=8453）*/
    private long chainId = 84532;

    /** 写入用的 RPC（与读 T2 共用 t2.rpc-urls 也行；分开方便专门用付费 RPC）*/
    private String rpcUrl = "https://sepolia.base.org";

    /** 广播交易账户私钥（持有少量 ETH 付 gas）*/
    private String publisherPrivateKey = "";

    /** gas 上限估算 */
    private long gasLimit = 300_000;

    /** 推送模式：admin（仅 admin 端点手动）/ auto（每次 T2 计算后自动推） */
    private String publishMode = "admin";
}
