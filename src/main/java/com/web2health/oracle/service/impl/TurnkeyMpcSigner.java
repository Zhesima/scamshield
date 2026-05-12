package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web2health.oracle.config.TurnkeyProperties;
import com.web2health.oracle.dto.collector.OnChainData;
import com.web2health.oracle.service.SignerService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Turnkey MPC 签名器（生产推荐）
 *
 * 工作流程：
 *   1. 后端构造与本地签名一致的 payload
 *   2. 加 Ethereum signed message prefix → keccak256
 *   3. 调 Turnkey REST API /public/v1/submit/sign_raw_payload，远程签 hash
 *   4. Turnkey HSM 内的 secp256k1 私钥签名 → 返回 r/s/v
 *   5. 拼成 65 字节签名返回
 *
 * 激活：oracle-signer.provider=turnkey + 配置 turnkey.* 凭证
 *
 * 与 LocalEcKeyPairSigner 输出 100% 兼容（同样 payload 格式 + 同样签名 scheme），
 * 链上 HealthOracle 合约无需任何改动
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "oracle-signer.provider", havingValue = "turnkey")
@RequiredArgsConstructor
public class TurnkeyMpcSigner implements SignerService {

    private final TurnkeyProperties props;
    private final ObjectMapper objectMapper;

    private TurnkeyApiStamper stamper;
    private WebClient client;

    @PostConstruct
    public void init() {
        if (props.getOrganizationId().isBlank()
                || props.getApiPublicKey().isBlank()
                || props.getApiPrivateKey().isBlank()
                || props.getSignerAddress().isBlank()) {
            throw new IllegalStateException(
                    "Turnkey 凭证不完整：organization-id / api-public-key / api-private-key / signer-address 必须全部配置");
        }
        stamper = new TurnkeyApiStamper(props.getApiPrivateKey(), props.getApiPublicKey());
        client = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("Content-Type", "application/json")
                .build();
        log.info("TurnkeyMpcSigner 启用: org={}, signerAddress={}",
                props.getOrganizationId(), props.getSignerAddress());
    }

    @Override
    public Signature sign(int chainId, String tokenAddress, int score, OnChainData onChain) {
        long signedAt = Instant.now().getEpochSecond();
        String payload = String.format(
                "Web2HealthOracle\nchain=%d\ntoken=%s\nscore=%d\nblock=%d\nblockHash=%s\nat=%d",
                chainId, tokenAddress.toLowerCase(), score,
                onChain.getBlockNumber(), onChain.getBlockHash(), signedAt);

        // 构造 Ethereum signed message prefix → keccak256（Turnkey 不会自动加 prefix）
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String prefix = "Ethereum Signed Message:\n" + payloadBytes.length;
        byte[] prefixed = new byte[prefix.length() + payloadBytes.length];
        System.arraycopy(prefix.getBytes(StandardCharsets.UTF_8), 0, prefixed, 0, prefix.length());
        System.arraycopy(payloadBytes, 0, prefixed, prefix.length(), payloadBytes.length);
        byte[] hash = Hash.sha3(prefixed);
        String hashHex = "0x" + Numeric.toHexStringNoPrefix(hash);

        // 远程签
        String txnHash = remoteSignSecp256k1(hashHex);

        // Turnkey 返回 r/s/v 已是 hex，拼接后加 0x
        return new Signature(
                props.getSignerAddress(),
                txnHash,
                "Web2HealthOracle",
                "1",
                "keccak256-secp256k1",
                signedAt
        );
    }

    /** 调 Turnkey 远程签 32 字节 hash，返回 65 字节 hex 签名 (0x...rs+v) */
    private String remoteSignSecp256k1(String hashHex) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("type", "ACTIVITY_TYPE_SIGN_RAW_PAYLOAD_V2");
            body.put("timestampMs", String.valueOf(System.currentTimeMillis()));
            body.put("organizationId", props.getOrganizationId());
            ObjectNode params = body.putObject("parameters");
            params.put("signWith", props.getSignerAddress());
            params.put("payload", hashHex);
            params.put("encoding", "PAYLOAD_ENCODING_HEXADECIMAL");
            params.put("hashFunction", "HASH_FUNCTION_NO_OP"); // 已经 hash 过

            String bodyStr = objectMapper.writeValueAsString(body);
            String stamp = stamper.stamp(bodyStr);

            JsonNode resp = client.post()
                    .uri("/public/v1/submit/sign_raw_payload")
                    .header("X-Stamp", stamp)
                    .bodyValue(bodyStr)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (resp == null) {
                throw new RuntimeException("Turnkey 响应为空");
            }
            JsonNode result = resp.path("activity").path("result").path("signRawPayloadResult");
            if (result.isMissingNode() || result.isNull()) {
                throw new RuntimeException("Turnkey 响应缺少 signRawPayloadResult: " + resp);
            }
            String r = result.path("r").asText();
            String s = result.path("s").asText();
            String v = result.path("v").asText();   // "00" 或 "01"，需要加 27 转 27/28

            int vInt = Integer.parseInt(v, 16) + 27;
            return "0x" + r + s + String.format("%02x", vInt);

        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Turnkey sign API 调用异常: " + ex.getMessage(), ex);
        }
    }
}
