package com.web2health.oracle.service.impl;

import com.web2health.oracle.dto.collector.OnChainData;
import com.web2health.oracle.service.SignerService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * 本地 secp256k1 密钥对签名器（dev / 单实例 prd 用）
 *
 * 配置：
 *   t2.signer-private-key 配置一个 64 位 hex 私钥（带 0x 前缀），未配置自动生成临时密钥
 *
 * 签名格式（简化版，非完整 EIP-712）：
 *   payload = "Web2HealthOracle\nchain={}\ntoken={}\nscore={}\nblock={}\nblockHash={}\nat={}"
 *   prefixed = "\x19Ethereum Signed Message:\n" + len(payload) + payload
 *   hash = keccak256(prefixed)
 *   signature = secp256k1.sign(hash, privateKey) → r(32) + s(32) + v(1) = 65 字节
 *
 * 验证（消费方）：
 *   1. 用同样的格式构造 message
 *   2. keccak256 → hash
 *   3. ecrecover(hash, v, r, s) == signer_address
 *
 * 升级路径：T3 上链 oracle 时改用完整 EIP-712 typed data，便于合约 ecrecover
 */
@Slf4j
@Service
@ConditionalOnExpression("'${oracle-signer.provider:local}' != 'turnkey'")
public class LocalEcKeyPairSigner implements SignerService {

    @Value("${t2.signer-private-key:}")
    private String privateKeyHex;

    private ECKeyPair keyPair;
    private String signerAddress;

    @PostConstruct
    public void init() {
        if (StringUtils.hasText(privateKeyHex)) {
            keyPair = ECKeyPair.create(Numeric.toBigInt(privateKeyHex.trim()));
            log.info("T2 signer 加载配置私钥, 公钥地址 = 0x{}", Keys.getAddress(keyPair));
        } else {
            // 临时密钥（每次重启变），仅 dev 演示用
            byte[] seed = new SecureRandom().generateSeed(32);
            keyPair = ECKeyPair.create(Numeric.toBigInt(seed));
            log.warn("T2 signer 未配置私钥，自动生成临时密钥（每次重启会变化），仅供本地演示");
            log.warn("生产部署必须设置 t2.signer-private-key 或接入 Turnkey MPC");
        }
        signerAddress = "0x" + Keys.getAddress(keyPair);
        log.info("T2 signer 公钥地址：{}", signerAddress);
    }

    @Override
    public Signature sign(int chainId, String tokenAddress, int score, OnChainData onChain) {
        long signedAt = Instant.now().getEpochSecond();

        // 待签 payload：固定格式确保消费方能复现
        String payload = String.format(
                "Web2HealthOracle\nchain=%d\ntoken=%s\nscore=%d\nblock=%d\nblockHash=%s\nat=%d",
                chainId,
                tokenAddress.toLowerCase(),
                score,
                onChain.getBlockNumber(),
                onChain.getBlockHash(),
                signedAt
        );
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        // 用 Ethereum personal_sign 风格（"\x19Ethereum Signed Message:\n<len>" prefix）
        // 这样消费方可用 ecrecover + signedPrefixedMessageToKey 验证，兼容标准 EVM 钱包
        Sign.SignatureData sig = Sign.signPrefixedMessage(payloadBytes, keyPair);

        // 拼成 65 字节 hex：r(32) + s(32) + v(1)
        String value = "0x"
                + Numeric.toHexStringNoPrefixZeroPadded(Numeric.toBigInt(sig.getR()), 64)
                + Numeric.toHexStringNoPrefixZeroPadded(Numeric.toBigInt(sig.getS()), 64)
                + String.format("%02x", sig.getV()[0] & 0xff);

        return new Signature(
                signerAddress,
                value,
                "Web2HealthOracle",
                "1",
                "keccak256-secp256k1",
                signedAt
        );
    }
}
