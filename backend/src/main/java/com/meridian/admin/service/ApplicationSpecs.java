package com.meridian.admin.service;

import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.ApplicationStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds the review queue query from optional filters. */
public final class ApplicationSpecs {

    private ApplicationSpecs() {
    }

    public static Specification<Application> queue(Collection<ApplicationStatus> statuses, String search,
                                                   UUID assignedTo) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            var pharmacy = root.join("pharmacy");

            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (assignedTo != null) {
                predicates.add(cb.equal(root.get("assignedReviewer").get("id"), assignedTo));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + escapeLike(search.trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(pharmacy.get("businessName")), pattern, '\\'),
                        cb.like(cb.lower(pharmacy.get("ownerName")), pattern, '\\'),
                        cb.like(cb.lower(pharmacy.get("licenseNumber")), pattern, '\\'),
                        cb.like(cb.lower(pharmacy.get("city")), pattern, '\\'),
                        cb.like(cb.lower(pharmacy.get("storefrontSlug")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Stops a user typing % or _ from acting as a wildcard. */
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
