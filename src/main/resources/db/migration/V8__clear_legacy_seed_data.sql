-- 清空 V3 预置的硬编码测试项目
-- 理由：T1 改用 (chain_id, token_address) 主键 + 懒加载，预置数据失去意义
-- 这些测试项目的 GitHub 路径多数 404，留着只会误导
-- 想要演示，调用 GET /v1/health/{chainId}/{tokenAddress} 触发懒加载即可

-- 注意：先删 health_snapshots 外键引用的快照（防止外键约束报错）
DELETE FROM health_snapshots WHERE project_id IN (
    SELECT id FROM projects WHERE slug IN ('illuvium', 'axie-infinity', 'stepn', 'gods-unchained', 'alien-worlds')
);

DELETE FROM projects WHERE slug IN ('illuvium', 'axie-infinity', 'stepn', 'gods-unchained', 'alien-worlds');
