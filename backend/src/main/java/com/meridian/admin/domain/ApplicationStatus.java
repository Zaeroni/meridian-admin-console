package com.meridian.admin.domain;

import java.util.EnumSet;
import java.util.Set;

public enum ApplicationStatus {
    SUBMITTED,
    IN_REVIEW,
    NEEDS_MORE_INFO,
    APPROVED,
    REJECTED,
    LIVE,
    SUSPENDED;

    /** Statuses where an application is waiting on someone, so time-in-queue is meaningful. */
    private static final Set<ApplicationStatus> OPEN = EnumSet.of(SUBMITTED, IN_REVIEW, NEEDS_MORE_INFO);

    public boolean isOpen() {
        return OPEN.contains(this);
    }
}
