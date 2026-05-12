/*
 * T2 签名验证脚本（独立运行）
 *
 * 用法：用 jbang / shebang 跑；或集成到测试里
 * 仅用于演示：消费方拿到 T2 响应后如何独立 ecrecover 出 signer 地址
 *
 * 步骤：
 *   1. 按服务端格式构造同样的 payload
 *   2. signMessage(payload, dummy_keypair, true) 算预期 hash（注意：实际是 keccak256 + Ethereum prefix）
 *   3. 从 65 字节签名拆出 r/s/v
 *   4. Sign.signedMessageToKey(payloadBytes, signatureData) → 公钥
 *   5. Keys.getAddress(public_key) → 验证 signer 地址
 *
 * 关键代码（用于消费方实现参考）：
 *
 *   String payload = String.format(
 *       "Web2HealthOracle\nchain=%d\ntoken=%s\nscore=%d\nblock=%d\nblockHash=%s\nat=%d",
 *       chainId, tokenAddress.toLowerCase(), score, blockNumber, blockHash, signedAt);
 *   byte[] signatureBytes = Numeric.hexStringToByteArray(signature.getValue());
 *   byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
 *   byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
 *   byte[] v = new byte[]{signatureBytes[64]};
 *   Sign.SignatureData sig = new Sign.SignatureData(v, r, s);
 *   BigInteger pubKey = Sign.signedPrefixedMessageToKey(payload.getBytes(), sig);
 *   String recoveredAddress = "0x" + Keys.getAddress(pubKey);
 *   assert recoveredAddress.equalsIgnoreCase(signature.getSignerAddress());
 */
