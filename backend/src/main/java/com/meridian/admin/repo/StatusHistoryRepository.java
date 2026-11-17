package com.meridian.admin.repo;

import com.meridian.admin.domain.StatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    @EntityGraph(attributePaths = {"actor"})
    List<StatusHistory> findByApplicationIdOrderByCreatedAtAscIdAsc(UUID applicationId);
}
