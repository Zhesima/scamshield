// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test, console2} from "forge-std/Test.sol";
import {HealthOracle} from "../src/HealthOracle.sol";

contract HealthOracleTest is Test {
    HealthOracle oracle;
    address owner;
    uint256 oraclePk;
    address oracleAddr;
    address otherUser;

    function setUp() public {
        owner = makeAddr("owner");
        otherUser = makeAddr("other");
        oraclePk = 0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318;
        oracleAddr = vm.addr(oraclePk);

        vm.prank(owner);
        oracle = new HealthOracle(oracleAddr);
    }

    function test_initialState() public {
        assertEq(oracle.owner(), owner);
        assertTrue(oracle.authorizedOracles(oracleAddr));
    }

    function test_publishScore_success() public {
        uint256 chainId = 8453;
        address token = 0x940181a94A35A4569E4529A3CDfB74e38FD98631;
        uint8 score = 78;
        uint64 blockNumber = 45678728;
        bytes32 blockHash = 0x657a5f7589c57674d0300ec4b03a79e000755ac7d7e9b112b947764bf8cbf4c3;
        uint64 timestamp = uint64(block.timestamp);

        bytes memory sig = _sign(oraclePk, chainId, token, score, blockNumber, blockHash, timestamp);
        oracle.publishScore(chainId, token, score, blockNumber, blockHash, timestamp, sig);

        HealthOracle.ScoreRecord memory r = oracle.getScore(chainId, token);
        assertEq(r.score, score);
        assertEq(r.blockNumber, blockNumber);
        assertEq(r.blockHash, blockHash);
        assertEq(r.signer, oracleAddr);
    }

    function test_publishScore_rejectsUnauthorizedSigner() public {
        uint256 randomPk = 0xDEAD;
        bytes memory sig = _sign(randomPk, 1, address(0xAA), 50, 100, bytes32(0), uint64(block.timestamp));
        vm.expectRevert("HealthOracle: signer not authorized");
        oracle.publishScore(1, address(0xAA), 50, 100, bytes32(0), uint64(block.timestamp), sig);
    }

    function test_publishScore_rejectsStaleTimestamp() public {
        // Foundry 默认 block.timestamp=1，warp 到安全值避免 uint64 下溢
        vm.warp(1_000_000);
        uint64 t1 = uint64(block.timestamp);
        bytes memory sig1 = _sign(oraclePk, 1, address(0xBB), 70, 100, bytes32(0), t1);
        oracle.publishScore(1, address(0xBB), 70, 100, bytes32(0), t1, sig1);

        // 更早的 timestamp 必须被拒
        uint64 t0 = t1 - 100;
        bytes memory sig0 = _sign(oraclePk, 1, address(0xBB), 80, 100, bytes32(0), t0);
        vm.expectRevert("HealthOracle: stale timestamp");
        oracle.publishScore(1, address(0xBB), 80, 100, bytes32(0), t0, sig0);
    }

    function test_publishScore_rejectsScoreOver100() public {
        bytes memory sig = _sign(oraclePk, 1, address(0xCC), 101, 100, bytes32(0), uint64(block.timestamp));
        vm.expectRevert("HealthOracle: score > 100");
        oracle.publishScore(1, address(0xCC), 101, 100, bytes32(0), uint64(block.timestamp), sig);
    }

    function test_addOracle_onlyOwner() public {
        address newOracle = makeAddr("newOracle");
        vm.prank(otherUser);
        vm.expectRevert("HealthOracle: not owner");
        oracle.addOracle(newOracle);

        vm.prank(owner);
        oracle.addOracle(newOracle);
        assertTrue(oracle.authorizedOracles(newOracle));
    }

    function test_getScoreWithAge_unsetReturnsMaxAge() public {
        (uint8 s, uint256 age, address signer) = oracle.getScoreWithAge(99, address(0xFF));
        assertEq(s, 0);
        assertEq(age, type(uint256).max);
        assertEq(signer, address(0));
    }

    // ── helper：复刻后端签名格式 ──
    function _sign(uint256 pk, uint256 chainId, address token, uint8 score, uint64 blockNumber, bytes32 blockHash, uint64 timestamp)
        internal pure returns (bytes memory)
    {
        bytes memory payload = abi.encodePacked(
            "Web2HealthOracle\n",
            "chain=",     _uint2str(chainId),
            "\ntoken=",   _addrLower(token),
            "\nscore=",   _uint2str(uint256(score)),
            "\nblock=",   _uint2str(uint256(blockNumber)),
            "\nblockHash=", _bytes32Lower(blockHash),
            "\nat=",      _uint2str(uint256(timestamp))
        );
        bytes32 prefHash = keccak256(abi.encodePacked(
            "\x19Ethereum Signed Message:\n",
            _uint2str(payload.length),
            payload
        ));
        (uint8 v, bytes32 r, bytes32 s) = vm.sign(pk, prefHash);
        return abi.encodePacked(r, s, v);
    }

    function _uint2str(uint256 v) internal pure returns (bytes memory) {
        if (v == 0) return bytes("0");
        uint256 j = v; uint256 len;
        while (j != 0) { len++; j /= 10; }
        bytes memory buf = new bytes(len);
        uint256 k = len;
        while (v != 0) { k--; buf[k] = bytes1(uint8(48 + v % 10)); v /= 10; }
        return buf;
    }
    function _addrLower(address a) internal pure returns (bytes memory) {
        bytes20 raw = bytes20(a);
        bytes memory out = new bytes(42);
        out[0] = "0"; out[1] = "x";
        for (uint256 i = 0; i < 20; i++) {
            out[2 + 2*i]     = _hex(uint8(raw[i]) >> 4);
            out[2 + 2*i + 1] = _hex(uint8(raw[i]) & 0x0f);
        }
        return out;
    }
    function _bytes32Lower(bytes32 b) internal pure returns (bytes memory) {
        bytes memory out = new bytes(66);
        out[0] = "0"; out[1] = "x";
        for (uint256 i = 0; i < 32; i++) {
            out[2 + 2*i]     = _hex(uint8(b[i]) >> 4);
            out[2 + 2*i + 1] = _hex(uint8(b[i]) & 0x0f);
        }
        return out;
    }
    function _hex(uint8 n) internal pure returns (bytes1) {
        return n < 10 ? bytes1(uint8(48 + n)) : bytes1(uint8(87 + n));
    }
}
