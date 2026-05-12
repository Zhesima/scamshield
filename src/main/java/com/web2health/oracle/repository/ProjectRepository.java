package com.web2health.oracle.repository;

import com.web2health.oracle.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** 老接口兼容：按 slug 查询 */
    Optional<Project> findBySlug(String slug);

    /** Web3 主查询：按 (chainId, tokenAddress) 查询，地址须传小写 */
    Optional<Project> findByChainIdAndTokenAddress(Integer chainId, String tokenAddress);

    /** 反查：按 CoinGecko ID 查询 */
    Optional<Project> findByCoingeckoId(String coingeckoId);
}
