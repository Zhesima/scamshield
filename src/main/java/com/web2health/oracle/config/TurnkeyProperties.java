package com.web2health.oracle.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Turnkey MPC 钱包配置（生产推荐）
 *
 * 注册流程（一次性）：
 *   1. https://app.turnkey.com 注册账号 → 创建 Organization
 *   2. 在 dashboard 创建 API key（生成 P-256 公私钥对）：
 *      记录 publicKey（hex）和 privateKey（hex）→ 填到下方
 *   3. 创建 Wallet → 在 Wallet 下创建 Private Key（curve=SECP256K1，addressFormat=ETHEREUM）
 *      记录 EVM address（用作 signWith）和该 key 的 ID
 *   4. 把 Org ID / API key / Private Key ID 配到环境变量
 *
 * 启用：oracle-signer.provider=turnkey 时激活 TurnkeyMpcSigner（替代 LocalEcKeyPairSigner）
 *
 * 安全模型：
 *   - secp256k1 私钥永不出 Turnkey 的 secure enclave（HSM）
 *   - 所有 API 调用用 P-256 stamp 签名，Turnkey 后端验证后才执行
 *   - 即便服务器被入侵，攻击者只能拿到 Turnkey API 凭证，无法导出 secp256k1 私钥
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "oracle-signer.turnkey")
public class TurnkeyProperties {

    /** Organization ID（UUID 格式）*/
    private String organizationId = "";

    /** P-256 API public key (compressed hex, 64 chars + 02/03 prefix = 66 chars)*/
    private String apiPublicKey = "";

    /** P-256 API private key (32-byte hex, 64 chars) */
    private String apiPrivateKey = "";

    /** Wallet 下的 secp256k1 private key 的 signWith 地址（EVM 0x-地址）*/
    private String signerAddress = "";

    /** Turnkey API base URL */
    private String baseUrl = "https://api.turnkey.com";
}
