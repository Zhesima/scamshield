package com.web2health.oracle.service.impl;

import com.web2health.oracle.dto.collector.OnChainData;
import com.web2health.oracle.service.SignerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalEcKeyPairSigner — 签名 + ecrecover 验证")
class LocalEcKeyPairSignerTest {

    private LocalEcKeyPairSigner signer;

    @BeforeEach
    void init() {
        signer = new LocalEcKeyPairSigner();
        // 用一个固定的测试私钥，方便复现
        ReflectionTestUtils.setField(signer, "privateKeyHex",
                "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318");
        signer.init();
    }

    @Test
    @DisplayName("ecrecover 反推地址等于 signer 地址")
    void signature_canBeRecovered() {
        OnChainData data = OnChainData.builder()
                .blockNumber(123_456_789L)
                .blockHash("0xdeadbeef" + "0".repeat(56))
                .totalSupply(BigInteger.valueOf(1_000_000_000L))
                .decimals(18)
                .liquidityUsd(1_500_000.0)
                .volume24hUsd(800_000.0)
                .build();
        int chainId = 8453;
        String token = "0x940181a94a35a4569e4529a3cdfb74e38fd98631";
        int score = 78;

        SignerService.Signature sig = signer.sign(chainId, token, score, data);

        // 1. 签名长度 65 字节 hex = "0x" + 130 chars
        assertEquals(132, sig.value().length(), "签名应为 65 字节");
        assertTrue(sig.value().startsWith("0x"));

        // 2. 复现 payload（消费方按同样格式构造）
        String payload = String.format(
                "Web2HealthOracle\nchain=%d\ntoken=%s\nscore=%d\nblock=%d\nblockHash=%s\nat=%d",
                chainId, token, score, data.getBlockNumber(), data.getBlockHash(), sig.signedAt()
        );

        // 3. 拆 r / s / v
        byte[] sigBytes = Numeric.hexStringToByteArray(sig.value());
        byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
        byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);
        byte[] v = new byte[]{sigBytes[64]};
        Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);

        // 4. ecrecover：从签名 + 原文恢复公钥
        try {
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(
                    payload.getBytes(), signatureData);
            String recoveredAddress = "0x" + Keys.getAddress(publicKey);
            assertEquals(sig.signerAddress().toLowerCase(), recoveredAddress.toLowerCase(),
                    "ecrecover 出来的地址必须等于 signer 地址");
        } catch (Exception ex) {
            fail("ecrecover 失败: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("不同 score 产生不同签名（非确定性输入）")
    void differentScoreYieldsDifferentSignature() {
        OnChainData data = OnChainData.builder()
                .blockNumber(100L).blockHash("0x" + "00".repeat(32))
                .totalSupply(BigInteger.ONE).decimals(18).build();
        SignerService.Signature s1 = signer.sign(1, "0x" + "ab".repeat(20), 50, data);
        // 等一秒确保 signedAt 不同（即使 score 一样签名也会变）
        try { Thread.sleep(1100); } catch (InterruptedException ignored) {}
        SignerService.Signature s2 = signer.sign(1, "0x" + "ab".repeat(20), 80, data);
        assertNotEquals(s1.value(), s2.value());
    }
}
