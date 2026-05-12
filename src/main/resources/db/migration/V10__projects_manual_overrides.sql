-- 人工覆盖字段锁定机制
-- 场景：CoinGecko 数据质量参差（如 ILV 只给 org URL 没具体 repo），
--      运维人员通过 admin 接口手填正确值后，下次 auto-refresh 不应覆盖
--
-- manual_overrides 存储已被人工覆盖的字段名（JSON 数组）
-- applyMetadata 刷新元数据前会检查此列表，跳过被锁字段
ALTER TABLE projects
    ADD COLUMN manual_overrides JSON NULL
        COMMENT '已被人工覆盖的字段名列表（["githubRepo","discordGuildId"]），auto-refresh 时跳过这些字段'
        AFTER metadata_source;
