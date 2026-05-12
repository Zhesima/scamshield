// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title  HealthOracle
 * @notice T3 链上健康分预言机
 *
 *  设计：
 *    - 任何被 owner 授权的 oracle 节点都可以 publishScore（多签共识可在外侧实现）
 *    - 每条记录按 (chainId, tokenAddress) 索引，存最新一条
 *    - 签名格式与后端 LocalEcKeyPairSigner 完全一致：
 *        payload = "Web2HealthOracle\nchain={chainId}\ntoken={tokenAddress}\nscore={score}\nblock={block}\nblockHash={blockHash}\nat={timestamp}"
 *        prefixed = "\x19Ethereum Signed Message:\n" + len + payload
 *        hash = keccak256(prefixed)
 *        signature = secp256k1.sign(hash, oraclePrivateKey)
 *    - 防重放：要求新 timestamp > 已存储 timestamp
 *
 *  消费方（其他合约）调用：
 *      ScoreRecord memory r = healthOracle.getScore(chainId, tokenAddr);
 *      require(r.timestamp >= block.timestamp - 1 days, "stale");
 *      require(r.score >= 50, "unhealthy");
 */
contract HealthOracle {

    // ── 类型 ──────────────────────────────────────────────────────────────
    struct ScoreRecord {
        uint8   score;         // 0-100
        uint64  blockNumber;   // off-chain 计算所基于的区块
        bytes32 blockHash;     // 同上区块 hash（防重放）
        uint64  timestamp;     // signer 签名时间戳
        address signer;        // 实际签名的 oracle 节点地址
    }

    // ── 存储 ──────────────────────────────────────────────────────────────
    address public owner;

    /** key = keccak256(abi.encode(chainId, tokenAddress)) → 最新评分 */
    mapping(bytes32 => ScoreRecord) public scores;

    /** 授权的 oracle 节点白名单 */
    mapping(address => bool) public authorizedOracles;

    // ── 事件 ──────────────────────────────────────────────────────────────
    event ScorePublished(
        uint256 indexed chainId,
        address indexed tokenAddress,
        uint8 score,
        uint64 blockNumber,
        uint64 timestamp,
        address signer
    );
    event OracleAdded(address indexed oracle);
    event OracleRemoved(address indexed oracle);
    event OwnerTransferred(address indexed previousOwner, address indexed newOwner);

    // ── 修饰符 ────────────────────────────────────────────────────────────
    modifier onlyOwner() {
        require(msg.sender == owner, "HealthOracle: not owner");
        _;
    }

    constructor(address initialOracle) {
        owner = msg.sender;
        if (initialOracle != address(0)) {
            authorizedOracles[initialOracle] = true;
            emit OracleAdded(initialOracle);
        }
    }

    // ── Owner 操作 ────────────────────────────────────────────────────────
    function addOracle(address oracle) external onlyOwner {
        require(oracle != address(0), "HealthOracle: zero address");
        require(!authorizedOracles[oracle], "HealthOracle: already authorized");
        authorizedOracles[oracle] = true;
        emit OracleAdded(oracle);
    }

    function removeOracle(address oracle) external onlyOwner {
        require(authorizedOracles[oracle], "HealthOracle: not authorized");
        authorizedOracles[oracle] = false;
        emit OracleRemoved(oracle);
    }

    function transferOwnership(address newOwner) external onlyOwner {
        require(newOwner != address(0), "HealthOracle: zero address");
        emit OwnerTransferred(owner, newOwner);
        owner = newOwner;
    }

    // ── 发布评分（任何人都可以调，签名校验决定是否接受）──────────────────
    function publishScore(
        uint256 chainId,
        address tokenAddress,
        uint8 score,
        uint64 blockNumber,
        bytes32 blockHash,
        uint64 timestamp,
        bytes calldata signature
    ) external {
        require(score <= 100, "HealthOracle: score > 100");
        require(tokenAddress != address(0), "HealthOracle: zero token");
        require(timestamp <= block.timestamp + 60, "HealthOracle: future timestamp");

        // 1. 重建后端签的 payload（必须和 LocalEcKeyPairSigner.sign 一字不差）
        bytes memory payload = abi.encodePacked(
            "Web2HealthOracle\n",
            "chain=",     _uint2str(chainId),
            "\ntoken=",   _addressToLowerHex(tokenAddress),
            "\nscore=",   _uint2str(uint256(score)),
            "\nblock=",   _uint2str(uint256(blockNumber)),
            "\nblockHash=", _bytes32ToLowerHex(blockHash),
            "\nat=",      _uint2str(uint256(timestamp))
        );

        // 2. ecrecover 恢复签名者
        address signer = _recoverSignerFromPrefixed(payload, signature);
        require(authorizedOracles[signer], "HealthOracle: signer not authorized");

        // 3. 防重放：新数据时间戳必须严格更新
        bytes32 key = keccak256(abi.encode(chainId, tokenAddress));
        ScoreRecord memory existing = scores[key];
        require(timestamp > existing.timestamp, "HealthOracle: stale timestamp");

        // 4. 写入
        scores[key] = ScoreRecord({
            score:       score,
            blockNumber: blockNumber,
            blockHash:   blockHash,
            timestamp:   timestamp,
            signer:      signer
        });

        emit ScorePublished(chainId, tokenAddress, score, blockNumber, timestamp, signer);
    }

    // ── 链上 Read API ────────────────────────────────────────────────────
    function getScore(uint256 chainId, address tokenAddress) external view returns (ScoreRecord memory) {
        return scores[keccak256(abi.encode(chainId, tokenAddress))];
    }

    /** 返回 (score, ageSeconds)，方便链上消费方判定数据新鲜度 */
    function getScoreWithAge(uint256 chainId, address tokenAddress)
        external view returns (uint8 score, uint256 ageSeconds, address signer)
    {
        ScoreRecord memory r = scores[keccak256(abi.encode(chainId, tokenAddress))];
        if (r.timestamp == 0) return (0, type(uint256).max, address(0));
        return (r.score, block.timestamp - r.timestamp, r.signer);
    }

    // ── 内部：ecrecover prefixed message ───────────────────────────────────
    function _recoverSignerFromPrefixed(bytes memory payload, bytes calldata sig) internal pure returns (address) {
        require(sig.length == 65, "HealthOracle: bad sig length");
        bytes32 prefixedHash = keccak256(abi.encodePacked(
            "\x19Ethereum Signed Message:\n",
            _uint2str(payload.length),
            payload
        ));
        bytes32 r;
        bytes32 s;
        uint8 v;
        assembly {
            r := calldataload(sig.offset)
            s := calldataload(add(sig.offset, 32))
            v := byte(0, calldataload(add(sig.offset, 64)))
        }
        if (v < 27) v += 27;
        require(v == 27 || v == 28, "HealthOracle: bad v");
        return ecrecover(prefixedHash, v, r, s);
    }

    // ── 内部：utility (uint2str / hex)──────────────────────────────────────
    function _uint2str(uint256 v) internal pure returns (bytes memory) {
        if (v == 0) return bytes("0");
        uint256 j = v;
        uint256 len;
        while (j != 0) { len++; j /= 10; }
        bytes memory buf = new bytes(len);
        uint256 k = len;
        while (v != 0) {
            k--;
            buf[k] = bytes1(uint8(48 + v % 10));
            v /= 10;
        }
        return buf;
    }

    function _addressToLowerHex(address a) internal pure returns (bytes memory) {
        bytes20 raw = bytes20(a);
        bytes memory out = new bytes(42);
        out[0] = "0"; out[1] = "x";
        for (uint256 i = 0; i < 20; i++) {
            out[2 + 2*i]     = _toHex(uint8(raw[i]) >> 4);
            out[2 + 2*i + 1] = _toHex(uint8(raw[i]) & 0x0f);
        }
        return out;
    }

    function _bytes32ToLowerHex(bytes32 b) internal pure returns (bytes memory) {
        bytes memory out = new bytes(66);
        out[0] = "0"; out[1] = "x";
        for (uint256 i = 0; i < 32; i++) {
            out[2 + 2*i]     = _toHex(uint8(b[i]) >> 4);
            out[2 + 2*i + 1] = _toHex(uint8(b[i]) & 0x0f);
        }
        return out;
    }

    function _toHex(uint8 nibble) internal pure returns (bytes1) {
        return nibble < 10 ? bytes1(uint8(48 + nibble)) : bytes1(uint8(87 + nibble));
    }
}
