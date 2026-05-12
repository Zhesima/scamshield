package com.web2health.oracle.service;

import com.web2health.oracle.dto.collector.DiscordData;
import com.web2health.oracle.dto.collector.GithubData;
import com.web2health.oracle.dto.response.HealthScoreResponse;
import com.web2health.oracle.domain.entity.Project;

import java.util.Optional;

public interface HealthScoreEngine {

    /**
     * 根据采集数据计算健康评分
     *
     * @param project      项目信息
     * @param githubData   GitHub 数据（可能为 null，表示采集失败）
     * @param discordData  Discord 数据（可能为 null，表示采集失败或未配置）
     * @param cached       是否命中缓存
     * @param avgCommits30d 历史平均 commits_30d（用于风险标记判断）
     * @return 完整的健康评分响应
     */
    HealthScoreResponse calculate(
            Project project,
            Optional<GithubData> githubData,
            Optional<DiscordData> discordData,
            boolean cached,
            Optional<Double> avgCommits30d
    );
}
