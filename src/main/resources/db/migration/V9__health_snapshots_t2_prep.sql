-- 为 T2 (Hard Score with EIP-712 signature) 预留快照字段
-- T1 写入时这些字段全部为 NULL
-- T2 实施时填充，链下消费方可拿 block_number 复现计算 + 验签

ALTER TABLE health_snapshots
    ADD COLUMN block_number BIGINT NULL COMMENT 'T2 计算所基于的区块高度，T1 为 NULL' AFTER collected_at,
    ADD COLUMN block_hash VARCHAR(66) NULL COMMENT 'T2 计算所基于的区块 hash，防重放' AFTER block_number,
    ADD COLUMN signer VARCHAR(42) NULL COMMENT 'T2 oracle 签名地址（EVM 公钥派生），T1 为 NULL' AFTER block_hash,
    ADD COLUMN signature VARCHAR(132) NULL COMMENT 'T2 EIP-712 签名 0x... (65 字节 = 132 hex chars)，T1 为 NULL' AFTER signer;
