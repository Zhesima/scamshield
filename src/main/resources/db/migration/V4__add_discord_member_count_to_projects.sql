-- Widget API 只返回在线人数，总成员数需手动维护作为计算在线率的分母
-- 为空时评分引擎改用在线人数绝对值阈值
ALTER TABLE projects
    ADD COLUMN discord_member_count INT NULL COMMENT 'Discord 总成员数（手动维护，用于计算在线率）'
        AFTER discord_guild_id;
