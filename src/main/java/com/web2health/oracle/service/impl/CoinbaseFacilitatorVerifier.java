package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web2health.oracle.config.X402Properties;
import com.web2health.oracle.service.PaymentVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Coinbase x402 facilitator 验证器（生产用）
 *
 * 流程：
 *   1. 客户端 X-PAYMENT 是 base64 编码的 PaymentPayload（含转账签名）
 *   2. 服务器 POST 到 facilitator /verify 端点验签
 *   3. 验证通过后再 POST /settle 实际结算（链上提交转账）
 *
 * 文档：https://x402.org / Coinbase x402 SDK
 *
 * 激活条件：x402.facilitator-url 非空
 * 当前状态：占位实现，真正接入需要：
 *   - 解析 X-PAYMENT base64 payload
 *   - 调 facilitator REST API
 *   - 处理网络超时 / 重试
 *   - settle 完成后才真正放行
 */
@Slf4j
@Component
@ConditionalOnExpression("!'${x402.facilitator-url:}'.isEmpty()")
@RequiredArgsConstructor
public class CoinbaseFacilitatorVerifier implements PaymentVerifier {

    private final X402Properties props;
    private final ObjectMapper objectMapper;

    @Override
    public boolean verify(String xPaymentHeader, String resource) {
        log.info("【x402 facilitator】验证支付: resource={}", resource);
        try {
            WebClient client = WebClient.create(props.getFacilitatorUrl());

            ObjectNode body = objectMapper.createObjectNode();
            body.put("payment", xPaymentHeader);
            body.put("resource", resource);
            body.put("payTo", props.getPayTo());
            body.put("network", props.getNetwork());
            body.put("amount", String.valueOf(props.getAmountPerCall()));
            body.put("asset", props.getUsdcAsset());

            JsonNode resp = client.post()
                    .uri("/verify")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            boolean valid = resp != null && resp.path("isValid").asBoolean(false);
            if (valid) {
                // TODO: 调 /settle 提交链上交易，settled 后才真正放行
                log.info("x402 验证通过（settle 阶段待实现）: resource={}", resource);
            } else {
                log.warn("x402 验证失败: resp={}", resp);
            }
            return valid;
        } catch (Exception ex) {
            log.error("x402 facilitator 调用异常: {}", ex.getMessage());
            return false;
        }
    }
}
