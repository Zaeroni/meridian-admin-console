package com.meridian.admin.repo;

import com.meridian.admin.domain.Reviewer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewerRepository extends JpaRepository<Reviewer, UUID> {
    Optional<Reviewer> findByEmailIgnoreCase(String email);
}
