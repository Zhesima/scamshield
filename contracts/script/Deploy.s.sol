// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console2} from "forge-std/Script.sol";
import {HealthOracle} from "../src/HealthOracle.sol";

/**
 * @notice 部署 HealthOracle 合约到目标链
 *
 * 用法：
 *   forge script script/Deploy.s.sol \
 *     --rpc-url base_sepolia \
 *     --private-key $DEPLOYER_PRIVATE_KEY \
 *     --broadcast \
 *     --verify
 *
 * 环境变量：
 *   DEPLOYER_PRIVATE_KEY  ：部署者私钥（持有 owner 权限）
 *   INITIAL_ORACLE_ADDR   ：初始授权的 oracle signer 地址（后端用的那个公钥地址）
 *   BASESCAN_API_KEY      ：（可选）合约 verify 用
 */
contract Deploy is Script {
    function run() external returns (HealthOracle oracle) {
        address initialOracle = vm.envAddress("INITIAL_ORACLE_ADDR");

        vm.startBroadcast();
        oracle = new HealthOracle(initialOracle);
        vm.stopBroadcast();

        console2.log("HealthOracle deployed at:", address(oracle));
        console2.log("Initial oracle:", initialOracle);
        console2.log("Owner:", oracle.owner());
    }
}
