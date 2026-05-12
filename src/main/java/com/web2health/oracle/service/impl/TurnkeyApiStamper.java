package com.web2health.oracle.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.AlgorithmParameters;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Base64;

/**
 * Turnkey API 请求 stamper
 *
 * 每个 Turnkey API 调用必须带 X-Stamp 头：
 *   1. SHA-256 hash 请求 body
 *   2. 用 P-256 私钥 ECDSA 签 hash → DER signature
 *   3. 构造 stamp JSON: {publicKey, signature, scheme="SIGNATURE_SCHEME_TK_API_P256"}
 *   4. Base64URL 编码后作为 X-Stamp 值
 *
 * 仅依赖 JDK 标准 java.security，无需 BouncyCastle
 */
@Slf4j
public class TurnkeyApiStamper {

    private final PrivateKey privateKey;
    private final String publicKeyHex;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurnkeyApiStamper(String privateKeyHex, String publicKeyHex) {
        try {
            this.privateKey = loadP256PrivateKey(privateKeyHex);
            this.publicKeyHex = publicKeyHex;
        } catch (Exception ex) {
            throw new IllegalStateException("加载 Turnkey P-256 私钥失败: " + ex.getMessage(), ex);
        }
    }

    /** 给 body 生成 stamp（base64url 编码的 JSON）*/
    public String stamp(String body) {
        try {
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);

            // 注意：Java SHA256withECDSA 内部已经做 SHA-256，不要再 hash 一次
            Signature ecdsa = Signature.getInstance("SHA256withECDSA");
            ecdsa.initSign(privateKey);
            ecdsa.update(bodyBytes);
            byte[] derSig = ecdsa.sign();

            ObjectNode stampJson = objectMapper.createObjectNode();
            stampJson.put("publicKey", publicKeyHex);
            stampJson.put("signature", Numeric.toHexStringNoPrefix(derSig));
            stampJson.put("scheme", "SIGNATURE_SCHEME_TK_API_P256");

            String stampStr = objectMapper.writeValueAsString(stampJson);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(stampStr.getBytes(StandardCharsets.UTF_8));

        } catch (Exception ex) {
            throw new RuntimeException("Turnkey stamp 生成失败: " + ex.getMessage(), ex);
        }
    }

    /** 加载 P-256 私钥（hex 编码的 32 字节标量）*/
    private static PrivateKey loadP256PrivateKey(String hex) throws Exception {
        BigInteger s = Numeric.toBigInt(hex.startsWith("0x") ? hex : "0x" + hex);
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));  // == P-256
        ECParameterSpec ecSpec = params.getParameterSpec(ECParameterSpec.class);
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(s, ecSpec);
        return KeyFactory.getInstance("EC").generatePrivate(keySpec);
    }
}
