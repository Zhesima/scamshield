-- 健康评分快照表（每次采集后存储）
CREATE TABLE IF NOT EXISTS health_snapshots
(
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id             BIGINT         NOT NULL COMMENT '关联项目 ID',
    score                  INT            NOT NULL COMMENT '综合健康评分 (0-100)',
    verdict                VARCHAR(20)    NOT NULL COMMENT '评级：healthy/caution/weak/danger',

    -- GitHub 采集字段
    commits_30d            INT            NULL COMMENT '近30天提交次数',
    commits_trend          JSON           NULL COMMENT '近4周每周提交数数组',
    trend_direction        VARCHAR(20)    NULL COMMENT '趋势方向：rising/stable/declining/dead',
    contributors_30d       INT            NULL COMMENT '近30天活跃贡献者数量',
    open_issues            INT            NULL COMMENT '当前未关闭 Issue 数量',
    last_commit_at         DATETIME       NULL COMMENT '最后提交时间',

    -- Discord 采集字段
    discord_member_count   INT            NULL COMMENT 'Discord 总成员数',
    discord_presence_count INT            NULL COMMENT 'Discord 当前在线数',
    activity_ratio         DECIMAL(8, 6)  NULL COMMENT '在线率 = presence / member',

    -- 各维度得分
    dev_activity_score     INT            NULL COMMENT '开发活跃度维度得分 (0-100)',
    community_score        INT            NULL COMMENT '社区活跃度维度得分 (0-100)',
    game_traction_score    INT            NULL COMMENT '游戏牵引力维度得分，Phase 2 填充',

    -- 元数据
    risk_flags             JSON           NULL COMMENT '风险标记列表 JSON',
    missing_sources        VARCHAR(200)   NULL COMMENT '缺失数据源，逗号分隔',
    collected_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '采集时间',

    CONSTRAINT fk_snapshot_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    INDEX idx_snapshot_project_id (project_id),
    INDEX idx_snapshot_collected_at (collected_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '健康评分快照表';
