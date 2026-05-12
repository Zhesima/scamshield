-- 项目基础信息表
CREATE TABLE IF NOT EXISTS projects
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(100)  NOT NULL COMMENT '项目展示名称',
    slug             VARCHAR(100)  NOT NULL UNIQUE COMMENT '项目唯一标识符（URL 友好）',
    github_owner     VARCHAR(100)  NULL COMMENT 'GitHub 仓库所有者（用户名或组织名）',
    github_repo      VARCHAR(200)  NULL COMMENT 'GitHub 仓库名',
    discord_guild_id VARCHAR(50)   NULL COMMENT 'Discord 服务器 ID',
    steam_app_id     VARCHAR(20)   NULL COMMENT 'Steam 应用 ID（Phase 2）',
    description      VARCHAR(500)  NULL COMMENT '项目简介',
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',

    INDEX idx_projects_slug (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目基础信息表';
