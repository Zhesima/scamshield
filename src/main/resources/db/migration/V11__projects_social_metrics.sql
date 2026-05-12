-- 加社交平台指标字段（来自 CoinGecko community_data）
-- 这些字段支撑 social_reach 维度评分，无需额外 API 调用
ALTER TABLE projects
    ADD COLUMN twitter_followers INT NULL COMMENT 'Twitter 粉丝数（CoinGecko community_data.twitter_followers）' AFTER twitter_handle,
    ADD COLUMN telegram_users INT NULL COMMENT 'Telegram 频道用户数（CoinGecko community_data.telegram_channel_user_count）' AFTER twitter_followers,
    ADD COLUMN reddit_subscribers INT NULL COMMENT 'Reddit 订阅人数（CoinGecko community_data.reddit_subscribers）' AFTER telegram_users,
    ADD COLUMN category VARCHAR(64) NULL COMMENT '项目分类（DefiLlama category 或 CoinGecko categories[0]，如 Liquid Staking）' AFTER reddit_subscribers;

-- health_snapshots 同步加字段，便于趋势分析
ALTER TABLE health_snapshots
    ADD COLUMN twitter_followers INT NULL AFTER discord_presence_count,
    ADD COLUMN social_reach_score INT NULL AFTER community_score;
