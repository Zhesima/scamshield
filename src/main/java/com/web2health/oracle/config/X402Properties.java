package com.web2health.oracle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * x402 微支付配置
 *
 * x402 是 Coinbase + Anthropic 合推的 Web3 原生 API 计费协议：
 *   1. 客户端首次请求，服务器返回 HTTP 402 + payment-required JSON
 *   2. 客户端构造 USDC 转账（由钱包 SDK 自动完成），把签名放入 X-PAYMENT header
 *   3. 服务器验证后放行（直接验签 + 链上确认，或委托 Coinbase facilitator）
 *
 * 推荐 Base 链：gas 极低、Coinbase 原生、x402 facilitator 默认部署在 Base
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "x402")
public class X402Properties {

    /** 总开关：false 时所有请求自由通过（开发 / 不计费部署）*/
    private boolean enabled = false;

    /** 链 ID，对应 facilitator 验证用的网络。Base = 8453 */
    private String network = "base";

    /** 收款地址（你的 oracle 钱包）*/
    private String payTo = "";

    /** USDC 合约地址。Base 主网 USDC = 0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913 */
    private String usdcAsset = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913";

    /** 单次调用价格（USDC 最小单位，USDC 有 6 位小数，1000 = 0.001 USDC = $0.001）*/
    private long amountPerCall = 1000;

    /** 免费配额：每个 IP 在 windowSeconds 时间窗内的免费请求数 */
    private int freeTierQuota = 5;

    /** 配额时间窗口（秒）*/
    private int freeTierWindowSeconds = 60;

    /** Coinbase facilitator URL（生产用）；为空时用 stub verifier（dev / demo）*/
    private String facilitatorUrl = "";

    /** 哪些路径需要计费（前缀匹配，逗号分隔）*/
    private String pathPrefixes = "/v1/health/";
}
