package com.meridian.admin.repo;

import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.ApplicationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationRepository
        extends JpaRepository<Application, UUID>, JpaSpecificationExecutor<Application> {

    /** Queue listing. The entity graph loads pharmacy and reviewer in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = {"pharmacy", "assignedReviewer"})
    Page<Application> findAll(Specification<Application> spec, Pageable pageable);

    /** Row lock so two reviewers cannot push the same application through a transition at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Application a where a.id = :id")
    Optional<Application> findByIdForUpdate(@Param("id") UUID id);

    @Query("select a.status as status, count(a) as total from Application a group by a.status")
    List<StatusCount> countByStatus();

    @EntityGraph(attributePaths = {"pharmacy"})
    List<Application> findByStatusInAndStatusChangedAtBefore(Collection<ApplicationStatus> statuses, Instant cutoff);

    @EntityGraph(attributePaths = {"pharmacy"})
    List<Application> findByStatus(ApplicationStatus status);

    @Query("select a from Application a join fetch a.pharmacy p "
            + "where a.status = :status and p.licenseExpiration <= :cutoff")
    List<Application> findByStatusWithLicenseExpiringBy(@Param("status") ApplicationStatus status,
                                                        @Param("cutoff") LocalDate cutoff);

    interface StatusCount {
        ApplicationStatus getStatus();

        long getTotal();
    }
}
