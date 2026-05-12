package com.web2health.oracle.service.impl;

import com.web2health.oracle.config.X402Properties;
import com.web2health.oracle.service.PaymentVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 开发用 stub：X-PAYMENT 非空就放行，不真验证
 *
 * 激活条件：x402.facilitator-url 为空（即没配 Coinbase facilitator）
 * 用途：演示流程跑通，不接入真实 USDC 支付
 *
 * 生产环境：必须配 facilitator-url，让 CoinbaseFacilitatorVerifier 接管
 */
@Slf4j
@Component
@ConditionalOnExpression("'${x402.facilitator-url:}'.isEmpty()")
@RequiredArgsConstructor
public class StubPaymentVerifier implements PaymentVerifier {

    private final X402Properties props;

    @Override
    public boolean verify(String xPaymentHeader, String resource) {
        if (!StringUtils.hasText(xPaymentHeader)) return false;
        log.warn("【x402 STUB】跳过真实验证，仅检查 header 非空。生产必须配 x402.facilitator-url。 resource={}", resource);
        return true;
    }
}
