package com.web2health.oracle.exception;

public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(Long projectId) {
        super("项目不存在: " + projectId);
    }

    public ProjectNotFoundException(String identifier) {
        super("项目不存在: " + identifier);
    }

    public ProjectNotFoundException(int chainId, String tokenAddress) {
        super("聚合器中找不到该合约对应项目: chainId=" + chainId + ", token=" + tokenAddress);
    }
}
