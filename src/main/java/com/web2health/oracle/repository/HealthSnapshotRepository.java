package com.web2health.oracle.repository;

import com.web2health.oracle.domain.entity.HealthSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HealthSnapshotRepository extends JpaRepository<HealthSnapshot, Long> {

    @Query("SELECT h FROM HealthSnapshot h WHERE h.project.id = :projectId ORDER BY h.collectedAt DESC")
    List<HealthSnapshot> findLatestByProjectId(@Param("projectId") Long projectId, Pageable pageable);

    @Query("SELECT AVG(h.commits30d) FROM HealthSnapshot h WHERE h.project.id = :projectId AND h.commits30d IS NOT NULL")
    Optional<Double> findAvgCommits30dByProjectId(@Param("projectId") Long projectId);

    default Optional<HealthSnapshot> findLatestOneByProjectId(Long projectId) {
        List<HealthSnapshot> results = findLatestByProjectId(projectId,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
