package com.meridian.admin.service;

import com.meridian.admin.config.QueueProperties;
import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.web.Dtos.QueueLevel;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Decides how urgent a wait is. Review and in-review waits are on us, so they use the shorter limit.
 * Needs-more-info waits are on the applicant, so they use the longer one. A wait turns WARN at half
 * the critical limit.
 */
@Component
public class QueuePolicy {

    private final QueueProperties properties;

    public QueuePolicy(QueueProperties properties) {
        this.properties = properties;
    }

    /** Whole hours since the last status change, or null if the application is not waiting. */
    public Long hoursInQueue(ApplicationStatus status, Instant statusChangedAt, Instant now) {
        if (!status.isOpen()) {
            return null;
        }
        return Math.max(0, Duration.between(statusChangedAt, now).toHours());
    }

    public QueueLevel level(ApplicationStatus status, Instant statusChangedAt, Instant now) {
        Long hours = hoursInQueue(status, statusChangedAt, now);
        if (hours == null) {
            return QueueLevel.NONE;
        }
        long criticalAt = criticalAfterHours(status);
        if (hours >= criticalAt) {
            return QueueLevel.CRITICAL;
        }
        if (hours >= criticalAt / 2) {
            return QueueLevel.WARN;
        }
        return QueueLevel.OK;
    }

    public long criticalAfterHours(ApplicationStatus status) {
        return status == ApplicationStatus.NEEDS_MORE_INFO
                ? properties.attentionDaysMoreInfo() * 24L
                : properties.attentionHoursReview();
    }
}
