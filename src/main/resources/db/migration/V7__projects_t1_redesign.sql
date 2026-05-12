-- T1 重设计：projects 表退化为元数据缓存层
-- 主键身份从 (id/slug) 改为 (chain_id, token_address)
-- 新增字段对接 CoinGecko / DefiLlama 聚合器

-- 1. 加 Web3 身份字段
ALTER TABLE projects
    ADD COLUMN chain_id INT NULL COMMENT 'EVM 链 ID：1=Ethereum, 8453=Base, 137=Polygon, 42161=Arbitrum, 10=Optimism' AFTER id,
    ADD COLUMN token_address VARCHAR(66) NULL COMMENT 'ERC-20 合约地址，统一小写存储' AFTER chain_id;

-- 2. 加聚合器外部 ID（用于反查 / 元数据刷新）
ALTER TABLE projects
    ADD COLUMN coingecko_id VARCHAR(64) NULL COMMENT 'CoinGecko 项目 ID，如 illuvium' AFTER token_address,
    ADD COLUMN defillama_id VARCHAR(128) NULL COMMENT 'DefiLlama 协议 slug' AFTER coingecko_id;

-- 3. 加展示元数据（来自聚合器或人工补全）
ALTER TABLE projects
    ADD COLUMN symbol VARCHAR(32) NULL COMMENT 'Token 符号，如 ILV / AXS' AFTER name,
    ADD COLUMN website VARCHAR(500) NULL COMMENT '官网' AFTER description,
    ADD COLUMN logo_url VARCHAR(500) NULL COMMENT '项目 Logo' AFTER website,
    ADD COLUMN twitter_handle VARCHAR(64) NULL COMMENT 'Twitter handle，不带 @' AFTER discord_guild_id;

-- 4. 加缓存控制字段
ALTER TABLE projects
    ADD COLUMN metadata_fetched_at TIMESTAMP NULL COMMENT '元数据上次刷新时间，超过 TTL 触发重新拉取' AFTER twitter_handle,
    ADD COLUMN metadata_source VARCHAR(32) NULL COMMENT '元数据来源：coingecko / defillama / manual' AFTER metadata_fetched_at;

-- 5. 字段长度调整
-- name 100 太窄，部分项目全名会超
-- description 500 不够，CoinGecko 描述常见 1000+ 字符
-- slug 改为可空（懒加载时可由 coingecko_id 派生，老 API 兼容用）
ALTER TABLE projects
    MODIFY COLUMN name VARCHAR(255) NOT NULL COMMENT '项目名称',
    MODIFY COLUMN slug VARCHAR(64) NULL COMMENT 'URL 友好标识，老接口兼容用',
    MODIFY COLUMN description TEXT NULL COMMENT '项目描述';

-- 6. 索引
-- (chain_id, token_address) 是 Web3 主键，必须唯一
CREATE UNIQUE INDEX uk_projects_chain_token ON projects (chain_id, token_address);
-- coingecko_id 用于反查、元数据刷新
CREATE INDEX idx_projects_coingecko ON projects (coingecko_id);
