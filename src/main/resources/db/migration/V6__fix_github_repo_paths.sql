-- 修正测试项目的 GitHub 仓库路径
-- 原 V3 数据中部分路径指向不存在的仓库（返回 404），此处更新为已确认的公开仓库
-- 若仍报 404，程序会降级处理（跳过 GitHub 维度），不影响服务启动

-- Illuvium：主游戏为闭源，使用 IlluviumGame 组织下的合约审计仓库
UPDATE projects SET
    github_owner = 'IlluviumGame',
    github_repo  = 'illuvium-subgraph'
WHERE slug = 'illuvium';

-- Axie Infinity：Sky Mavis 旗下 Ronin 网络合约（已公开）
UPDATE projects SET
    github_owner = 'axieinfinity',
    github_repo  = 'ronin-dpos-contract'
WHERE slug = 'axie-infinity';

-- Gods Unchained：Immutable X 核心 SDK（已公开，代表 IMX 生态活跃度）
-- imx-core-sdk 已归档，改用 ts 版本
UPDATE projects SET
    github_owner = 'immutable',
    github_repo  = 'ts-immutable-sdk'
WHERE slug = 'gods-unchained';

-- Alien Worlds：WAX 区块链生态，使用其 API 仓库（已公开）
UPDATE projects SET
    github_owner = 'alien-worlds',
    github_repo  = 'aw-api-starter'
WHERE slug = 'alien-worlds';

-- STEPN：闭源，github 保持 NULL，无需修改
