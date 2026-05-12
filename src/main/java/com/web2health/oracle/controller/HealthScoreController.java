package com.web2health.oracle.controller;

import com.web2health.oracle.dto.response.HealthScoreResponse;
import com.web2health.oracle.service.HealthScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 健康评分接口
 *
 * 主接口：GET /v1/health/{chainId}/{tokenAddress}（推荐 Web3 agent 使用）
 * 老接口：GET /v1/project/{id_or_slug}/health-score（向后兼容）
 *
 * 状态码：
 *   200 全部数据源采集成功
 *   206 部分数据源采集失败（响应体含可用维度评分）
 *   400 入参不合法（链 ID 不支持 / 合约地址非法）
 *   404 项目不存在（包括聚合器也找不到）
 *   500 所有配置数据源全部瞬时故障
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class HealthScoreController {

    private final HealthScoreService healthScoreService;

    /** 0x + 40 位 hex */
    private static final Pattern EVM_ADDRESS = Pattern.compile("^0x[a-fA-F0-9]{40}$");

    // ── 主接口：Web3 链上身份查询 ─────────────────────────────────────────────
    @GetMapping("/health/{chainId}/{tokenAddress}")
    public ResponseEntity<HealthScoreResponse> getHealthByContract(
            @PathVariable int chainId,
            @PathVariable String tokenAddress,
            @RequestParam(defaultValue = "false") boolean realtime) {

        if (!EVM_ADDRESS.matcher(tokenAddress).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tokenAddress 必须为 0x 开头的 42 位 EVM 地址");
        }
        if (chainId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "chainId 必须为正整数");
        }

        log.info("收到 Web3 健康评分请求: chainId={}, token={}, realtime={}",
                chainId, tokenAddress, realtime);

        HealthScoreResponse response = healthScoreService.getHealthScoreByContract(
                chainId, tokenAddress, realtime);

        return wrapResponse(response);
    }

    // ── 老接口：按 ID / slug 查询，保留兼容 ───────────────────────────────────
    @GetMapping("/project/{id}/health-score")
    public ResponseEntity<HealthScoreResponse> getHealthScore(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean realtime) {

        log.info("收到旧式健康评分请求: identifier={}, realtime={}", id, realtime);

        HealthScoreResponse response = healthScoreService.getHealthScore(id, realtime);
        return wrapResponse(response);
    }

    /** 有 missing_sources 时返回 206 Partial Content */
    private ResponseEntity<HealthScoreResponse> wrapResponse(HealthScoreResponse response) {
        List<String> missing = response.getMetadata() != null
                ? response.getMetadata().getMissingSources() : null;
        boolean partial = missing != null && !missing.isEmpty();
        HttpStatus status = partial ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
