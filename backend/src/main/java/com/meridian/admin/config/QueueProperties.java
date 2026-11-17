package com.meridian.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thresholds for time-in-queue indicators and the "needs attention" list. */
@ConfigurationProperties(prefix = "meridian.queue")
public record QueueProperties(int attentionHoursReview, int attentionDaysMoreInfo, int licenseExpiryWarningDays) {
}
