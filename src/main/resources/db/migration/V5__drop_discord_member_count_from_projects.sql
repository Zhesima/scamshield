-- discord_member_count 改用在线人数绝对值评分后不再需要，删除手动维护字段
-- 注意：该列由 V4 添加，此处直接 DROP 不加 IF EXISTS（MySQL 8.0.4 以下不支持该语法）
ALTER TABLE projects DROP COLUMN discord_member_count;
