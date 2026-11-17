package com.meridian.admin.repo;

import com.meridian.admin.domain.Pharmacy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PharmacyRepository extends JpaRepository<Pharmacy, UUID> {
    boolean existsByStorefrontSlug(String storefrontSlug);
}
