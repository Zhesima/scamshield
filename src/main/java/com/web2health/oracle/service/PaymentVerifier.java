package com.web2health.oracle.service;

/**
 * x402 支付验证器
 *
 * 实现方：
 *   - StubPaymentVerifier：开发用，X-PAYMENT 非空就放行（不真验证）
 *   - CoinbaseFacilitatorVerifier：生产用，调 Coinbase facilitator API 验签 + 链上确认
 *
 * 配置 x402.facilitator-url 后自动切换到 Coinbase 实现
 */
public interface PaymentVerifier {

    /**
     * 验证 X-PAYMENT header 中的支付证明
     *
     * @param xPaymentHeader 请求头 X-PAYMENT 的 base64 值
     * @param resource       被支付保护的资源路径（用于 facilitator 验证 scope）
     * @return true=支付有效可放行；false=无效/失败
     */
    boolean verify(String xPaymentHeader, String resource);
}
