package com.web2health.oracle.controller;

import com.web2health.oracle.dto.response.T2HealthScoreResponse;
import com.web2health.oracle.service.T2HealthScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

/**
 * T2 Hard Score 接口
 *
 * 与 T1 的关键区别：
 *   - 路径前缀 /v2/ （vs /v1/）
 *   - 数据完全链上 + 块高 pinning + EIP-712 签名
 *   - 链下消费方可独立 ecrecover 验证签名
 *
 * x402 计费默认作用于 /v1/health/，不影响 /v2/。如要给 T2 加计费，
 * 在 application.yml 把 x402.path-prefixes 改成 "/v1/health/,/v2/health/"
 */
@Slf4j
@RestController
@RequestMapping("/v2")
@RequiredArgsConstructor
public class T2HealthController {

    private final T2HealthScoreService t2Service;

    private static final Pattern EVM_ADDRESS = Pattern.compile("^0x[a-fA-F0-9]{40}$");

    @GetMapping("/health/{chainId}/{tokenAddress}")
    public ResponseEntity<T2HealthScoreResponse> getHardScore(
            @PathVariable int chainId,
            @PathVariable String tokenAddress) {

        if (!EVM_ADDRESS.matcher(tokenAddress).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tokenAddress 必须为 0x 开头的 42 位 EVM 地址");
        }
        if (chainId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "chainId 必须为正整数");
        }

        log.info("收到 T2 hard score 请求: chainId={}, token={}", chainId, tokenAddress);
        T2HealthScoreResponse resp = t2Service.computeAndSign(chainId, tokenAddress);
        return ResponseEntity.ok(resp);
    }
}
