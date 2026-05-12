<div align="center">

# 🛡️ ScamShield

### The trust layer AI agents call before any swap.

**Signed token risk scores on Base · Paid via x402 · Rug-proof your agent.**

[![Built on Base](https://img.shields.io/badge/built%20on-Base-0052FF?style=flat-square&logo=coinbase&logoColor=white)](https://base.org)
[![Live Contract](https://img.shields.io/badge/contract-Base%20Sepolia-success?style=flat-square)](https://sepolia.basescan.org/address/0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF)
[![License](https://img.shields.io/badge/license-MIT-blue?style=flat-square)](LICENSE)
[![Twitter Follow](https://img.shields.io/twitter/follow/MarcoMa_9527?style=flat-square&logo=x&label=Follow)](https://twitter.com/MarcoMa_9527)

[Live Contract](https://sepolia.basescan.org/address/0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF) · [Demo Video](#demo) · [Architecture](#architecture) · [中文文档](#中文文档)

</div>

---

## What is ScamShield?

Every AI agent making on-chain transactions needs a **circuit breaker**. ScamShield is that circuit breaker.

Before any agent signs a swap, it makes an x402-paid call to our API and gets back a **secp256k1-signed token risk score**, verifiable on Base by anyone. Score below the agent's preset tolerance? It aborts the swap.

```
Agent  ──[x402 $0.001]──▶  ScamShield API  ──signed score──▶  Agent
                                  │
                                  └──[publish]──▶  HealthOracle.sol  on Base
                                                          │
                                                          └─ ecrecover verified
                                                          └─ replay-protected
                                                          └─ block-pinned
```

**Integrating costs one line of code. Verification is on-chain.**

---

## Why now

The agentic economy is here. Coinbase, Google, and SKALE launched the x402 standard in early 2026. Kite AI raised from Coinbase Ventures for agent infrastructure. **What's missing**: a trust layer agents can verify cryptographically — not just trust an API's word.

ScamShield is the first **agent-native, on-chain verifiable** trust signal. No SDK lock-in, no centralized list, no opaque scoring.

---

## Live deployment

| Field | Value |
|---|---|
| **Network** | Base Sepolia (chainId 84532) |
| **HealthOracle contract** | [`0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF`](https://sepolia.basescan.org/address/0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF) |
| **Deploy tx** | [`0xd11bb619...4ce6eac3`](https://sepolia.basescan.org/tx/0xd11bb619598c481c6daadd6dd2304579bbaa02af1457310bf00c1ace4ce6eac3) |
| **Latest score publish tx** | [`0x2c73069a...feb2b1370`](https://sepolia.basescan.org/tx/0x2c73069a6ec698d1766a6644f02ad9e426ada47579fffebf6fd2063feb2b1370) |
| **Authorized signer** | `0x2c7536e3605d9c16a7a3d7b1898e529396a65c23` |
| **Solidity** | 0.8.24 (Foundry) |
| **Tests** | 7/7 passing (forge test) |

Read the latest score directly from chain:

```bash
cast call 0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF \
  "getScoreWithAge(uint256,address)(uint8,uint256,address)" \
  8453 \
  0x940181a94A35A4569E4529A3CDfB74e38FD98631 \
  --rpc-url https://sepolia.base.org
```

Returns: `(score, ageInSeconds, signer)` — anyone can call, no API key.

---

## Demo

> _90-second demo video coming this week. Watch this section._

**Try the live API:**

```bash
# T2 hard score with on-chain signature (Aerodrome / Base)
curl https://api.scamshield.xyz/v2/health/8453/0x940181a94A35A4569E4529A3CDfB74e38FD98631

# Returns: { "score": 100, "tier": "T2", "signature": { ... block-pinned ... } }
```

> _API endpoint goes live once Vercel deploy is wired._

---

## Architecture

ScamShield uses a **three-tier trust model**: each tier strictly stronger than the last.

| Tier | Data source | Output | Trust | Endpoint |
|---|---|---|---|---|
| **T1 Soft** | CoinGecko + DefiLlama + GitHub + social | JSON | ⭐⭐ Off-chain signal | `/v1/health/:chainId/:token` |
| **T2 Hard** | Base RPC (totalSupply) + DexScreener (liquidity/volume) + block pinning + secp256k1 signature | JSON + signature | ⭐⭐⭐⭐ Cryptographically reproducible | `/v2/health/:chainId/:token` |
| **T3 Oracle** | T2 result published to HealthOracle.sol with on-chain ecrecover verification | On-chain `getScore()` | ⭐⭐⭐⭐⭐ Contract-grade | EVM call |

**Signature scheme** (matches off-chain backend and on-chain Solidity byte-for-byte):

```
payload   = "Web2HealthOracle\nchain=<chainId>\ntoken=<addr>\nscore=<n>\nblock=<num>\nblockHash=<hash>\nat=<ts>"
prefixed  = "\x19Ethereum Signed Message:\n" + len(payload) + payload
hash      = keccak256(prefixed)
signature = secp256k1.sign(hash, privateKey)   →  r(32) || s(32) || v(1) = 65 bytes
```

On-chain verification: [`HealthOracle.sol → _recoverSignerFromPrefixed()`](contracts/src/HealthOracle.sol).

---

## Quick integration (1 line of code)

```typescript
import { ScamShield } from "@scamshield/sdk";

const score = await ScamShield.getScore({
  chainId: 8453,
  token: "0x940181a94A35A4569E4529A3CDfB74e38FD98631",
  payment: x402Wallet,  // pays $0.001 USDC automatically
});

if (score.value < agent.riskTolerance) {
  return abort("ScamShield: token below trust threshold");
}
// otherwise proceed with swap, signature is verifiable on Base
```

> _SDK shipping in v0.2. Today: call `/v2/health/...` directly + verify signature client-side._

---

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.2 + Java 21 |
| Database | MySQL 8 + Flyway |
| Cache | Caffeine (in-process, 1h evaluation TTL + 24h metadata TTL) |
| HTTP | Spring WebClient (proxy-aware) |
| On-chain data | web3j 4.12 + Base RPC + DexScreener |
| Signing | secp256k1 (personal_sign compatible) |
| MPC wallet | Turnkey (production-ready) |
| Payment | x402 (HTTP 402 micropayment + USDC on Base) |
| Smart contracts | Solidity 0.8.24 + Foundry |

---

## Quick start (local dev)

```bash
# 1. Database
mysql -uroot -p
CREATE DATABASE web2health_oracle CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. Environment
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# edit values: GITHUB_TOKEN, COINGECKO_DEMO_KEY, t2.signer-private-key, t3.* (optional)

# 3. Run
mvn spring-boot:run

# 4. Test
curl http://localhost:8086/v1/health/8453/0x940181a94A35A4569E4529A3CDfB74e38FD98631
```

See [`CLAUDE.md`](CLAUDE.md) for detailed architecture and [`docs/T3-DEPLOYMENT-GUIDE.md`](docs/T3-DEPLOYMENT-GUIDE.md) for deploying the on-chain oracle.

---

## Roadmap

### Next 30 days

- [ ] Vercel landing + live token explorer
- [ ] TypeScript SDK (`@scamshield/sdk`) v0.1
- [ ] Telegram bot — paste contract, get instant score
- [ ] AI agent demo (Claude + x402) — autonomous swap circuit breaker

### Next 90 days

- [ ] Audit + Base mainnet deployment
- [ ] Chainlink CCIP for cross-chain score broadcast (Ethereum / Arbitrum / Optimism)
- [ ] Pyth volatility feed integration (T2 new dimension)
- [ ] 3 wallet integration POCs (Rabby / OKX Wallet / Coinbase Smart Wallet)

### Vision

ScamShield becomes the **default trust check** between any AI agent and any on-chain action.

---

## Project links

| Link | URL |
|---|---|
| 🌐 Landing (coming soon) | `https://scamshield.xyz` |
| 📦 GitHub | https://github.com/Zhesima/scamshield |
| 🐦 Twitter / X | [@MarcoMa_9527](https://twitter.com/MarcoMa_9527) |
| 📜 Contract on Base Sepolia | [Basescan](https://sepolia.basescan.org/address/0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF) |
| 📖 Deployment Guide | [docs/T3-DEPLOYMENT-GUIDE.md](docs/T3-DEPLOYMENT-GUIDE.md) |
| 📋 Monetization plan | [docs/MONETIZATION-PLAN.md](docs/MONETIZATION-PLAN.md) |

---

## License

MIT — fork it, embed it, build on top of it.

---

<a id="中文文档"></a>

## 中文文档

### ScamShield 是什么？

每个上链的 AI agent 都需要一道**熔断器**，ScamShield 就是这道熔断器。

Agent 在 swap 前先调我们 API，用 x402 自动付 0.001 美元 USDC，立刻拿到 Base 链上 secp256k1 签名的代币信任分数。分数低于 agent 预设阈值 → 自动放弃交易。**集成只要一行代码，签名链上可验证**。

### 三层信任模型

| Tier | 数据源 | 输出 | 信任级 |
|---|---|---|---|
| **T1 软信号** | CoinGecko + DefiLlama + GitHub + 社交指标 | JSON | ⭐⭐ |
| **T2 硬数据** | Base RPC + DexScreener + ECDSA 签名 + 块高 pinning | JSON + signature | ⭐⭐⭐⭐ |
| **T3 链上 Oracle** | T2 结果广播到 HealthOracle.sol，链上 ecrecover 验签 | 链上 `getScore()` | ⭐⭐⭐⭐⭐ |

### 链上部署（Base Sepolia）

| 字段 | 值 |
|---|---|
| HealthOracle 合约 | [`0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF`](https://sepolia.basescan.org/address/0xC4a564eb6AE006574c5a6B9A9c6cb1406eaEfaCF) |
| 部署交易 | [`0xd11bb619...`](https://sepolia.basescan.org/tx/0xd11bb619598c481c6daadd6dd2304579bbaa02af1457310bf00c1ace4ce6eac3) |
| 授权 signer | `0x2c7536e3605d9c16a7a3d7b1898e529396a65c23` |
| 测试通过 | forge test 7/7 ✅ |

### 本地启动

详见上方英文版 Quick start，或 [`CLAUDE.md`](CLAUDE.md)。

### 贡献

欢迎 PR / issue。联系：[@MarcoMa_9527](https://twitter.com/MarcoMa_9527)

---

<div align="center">

**🛡️ ScamShield — rug-proof your agent.**

Built with ❤️ on [Base](https://base.org).

</div>
