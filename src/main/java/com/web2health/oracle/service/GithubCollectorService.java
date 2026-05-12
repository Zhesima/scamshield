package com.web2health.oracle.service;

import com.web2health.oracle.dto.collector.GithubData;
import com.web2health.oracle.exception.DataCollectionException;

public interface GithubCollectorService {

    /**
     * 采集 GitHub 仓库的开发活跃度数据
     *
     * @param owner 仓库所有者（用户名或组织名）
     * @param repo  仓库名
     * @return 采集到的 GitHub 数据
     * @throws DataCollectionException 采集失败时抛出，不影响其他数据源
     */
    GithubData collect(String owner, String repo) throws DataCollectionException;
}
