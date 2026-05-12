# Web2 Health Oracle — On-chain Contracts (T3)

Solidity 合约 + Foundry 项目。把 T2 计算的健康分写到链上，让其他智能合约能直接读。

## 目录结构

```
contracts/
├── src/HealthOracle.sol        主合约：链上存储健康分 + 签名验证
├── script/Deploy.s.sol         Foundry 部署脚本
├── test/HealthOracle.t.sol     单测（含签名复现验证）
├── foundry.toml                Foundry 配置
└── README.md                   本文件
```

## 安装 Foundry

```bash
curl -L https://foundry.paradigm.xyz | bash
foundryup
```

## 拉取依赖（forge-std）

```bash
cd contracts
forge install foundry-rs/forge-std --no-commit
```

## 跑单测

```bash
forge test -vv
```

应输出 6/6 通过。

## 部署到 Base Sepolia 测试网

### 1. 准备账户

- 部署者私钥（owner，可后续 transferOwnership）
- 准备少量 Base Sepolia ETH 测试币：https://www.alchemy.com/faucets/base-sepolia
- 后端 oracle signer 地址（启动后端时日志里会打印 `T2 signer 公钥地址：0x...`）

### 2. 部署

```bash
export DEPLOYER_PRIVATE_KEY=0x...
export INITIAL_ORACLE_ADDR=0x... # 后端 signer 地址
export BASESCAN_API_KEY=...      # 可选，用于源码 verify

forge script script/Deploy.s.sol \
  --rpc-url base_sepolia \
  --private-key $DEPLOYER_PRIVATE_KEY \
  --broadcast \
  --verify
```

部署成功后会打印合约地址，记下来。

### 3. 配置后端

把合约地址填到 `application.yml` 的 `t3.health-oracle-address`。

```yaml
t3:
  enabled: true
  health-oracle-address: 0x... # 部署后地址
  rpc-url: https://sepolia.base.org
```

后端的 `OnchainOracleWriter` 会用这个地址 publishScore 上链。

## 合约接口

### 写

- `publishScore(chainId, token, score, blockNumber, blockHash, timestamp, signature)`
  - 任何人可调，但 signature 必须由授权 oracle 节点签名
  - timestamp 必须严格大于已存储的（防重放）
- `addOracle(address)` — 仅 owner
- `removeOracle(address)` — 仅 owner
- `transferOwnership(address)` — 仅 owner

### 读

- `getScore(chainId, token) → ScoreRecord`
- `getScoreWithAge(chainId, token) → (score, ageSeconds, signer)`
- `authorizedOracles(address) → bool`

## 链上消费方调用示例

```solidity
interface IHealthOracle {
    function getScoreWithAge(uint256 chainId, address token)
        external view returns (uint8 score, uint256 ageSeconds, address signer);
}

contract LendingPool {
    IHealthOracle constant ORACLE = IHealthOracle(0x...);  // 部署后填

    function maxBorrow(address token) external view returns (uint256) {
        (uint8 score, uint256 age, ) = ORACLE.getScoreWithAge(8453, token);
        require(age < 1 days, "stale oracle data");
        require(score >= 50, "unhealthy token, borrow blocked");
        return baseLimit * score / 100;  // 健康分越高借款上限越高
    }
}
```

## 签名格式（与后端 LocalEcKeyPairSigner 完全对齐）

```text
payload = "Web2HealthOracle\nchain={chainId}\ntoken={tokenAddress.lower()}\nscore={score}\nblock={blockNumber}\nblockHash={blockHash}\nat={timestamp}"

prefixed = "\x19Ethereum Signed Message:\n" + len(payload) + payload

hash = keccak256(prefixed)

signature = secp256k1.sign(hash, oraclePrivateKey) → r(32) || s(32) || v(1) = 65 bytes
```

合约里用同样格式重建 payload，再 `ecrecover(hash, v, r, s)` 出 signer，校验白名单。
