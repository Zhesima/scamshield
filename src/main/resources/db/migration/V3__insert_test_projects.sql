-- 预置5个测试项目数据
INSERT INTO projects (name, slug, github_owner, github_repo, discord_guild_id, description)
VALUES
    -- 1. Illuvium：github 有仓库，discord guild_id 待填
    ('Illuvium',
     'illuvium',
     'IlluviumGame',
     'illuvium',
     NULL,
     'Illuvium 是一款基于以太坊的开放世界 RPG 游戏，玩家可以捕捉和战斗名为 Illuvials 的生物'),

    -- 2. Axie Infinity：github 有合约仓库
    ('Axie Infinity',
     'axie-infinity',
     'axieinfinity',
     'axie-infinity-contracts',
     NULL,
     'Axie Infinity 是基于区块链的宠物养成与战斗游戏，开创了 Play-to-Earn 模式'),

    -- 3. STEPN：github 留空（闭源）
    ('STEPN',
     'stepn',
     NULL,
     NULL,
     NULL,
     'STEPN 是一款 Move-to-Earn 应用，用户通过步行、慢跑和户外跑步赚取加密货币'),

    -- 4. Gods Unchained：github 使用 imx-core-sdk
    ('Gods Unchained',
     'gods-unchained',
     'immutable',
     'imx-core-sdk',
     NULL,
     'Gods Unchained 是一款基于 Immutable X 的免费竞技卡牌游戏'),

    -- 5. Alien Worlds：github 有 API 仓库
    ('Alien Worlds',
     'alien-worlds',
     'alien-worlds',
     'alienworlds-api',
     NULL,
     'Alien Worlds 是基于 WAX 区块链的去中心化元宇宙游戏，玩家可以挖矿、战斗并参与 DAO 治理');
