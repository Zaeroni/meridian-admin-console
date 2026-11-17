package com.meridian.admin.service;

import static com.meridian.admin.domain.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.admin.config.QueueProperties;
import com.meridian.admin.web.Dtos.QueueLevel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QueuePolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-16T12:00:00Z");
    private final QueuePolicy policy = new QueuePolicy(new QueueProperties(48, 7, 30));

    private Instant ago(long hours) {
        return NOW.minus(Duration.ofHours(hours));
    }

    @Test
    void reviewWaitsTurnWarnAtHalfTheLimitAndCriticalAtTheLimit() {
        assertThat(policy.level(SUBMITTED, ago(5), NOW)).isEqualTo(QueueLevel.OK);
        assertThat(policy.level(SUBMITTED, ago(23), NOW)).isEqualTo(QueueLevel.OK);
        assertThat(policy.level(SUBMITTED, ago(24), NOW)).isEqualTo(QueueLevel.WARN);
        assertThat(policy.level(IN_REVIEW, ago(47), NOW)).isEqualTo(QueueLevel.WARN);
        assertThat(policy.level(IN_REVIEW, ago(48), NOW)).isEqualTo(QueueLevel.CRITICAL);
    }

    @Test
    void needsMoreInfoUsesTheLongerApplicantLimit() {
        assertThat(policy.level(NEEDS_MORE_INFO, ago(48), NOW)).isEqualTo(QueueLevel.OK);
        assertThat(policy.level(NEEDS_MORE_INFO, ago(84), NOW)).isEqualTo(QueueLevel.WARN);
        assertThat(policy.level(NEEDS_MORE_INFO, ago(24 * 7), NOW)).isEqualTo(QueueLevel.CRITICAL);
    }

    @Test
    void applicationsThatAreNotWaitingHaveNoQueueTime() {
        for (var status : new com.meridian.admin.domain.ApplicationStatus[]{APPROVED, REJECTED, LIVE, SUSPENDED}) {
            assertThat(policy.hoursInQueue(status, ago(500), NOW)).isNull();
            assertThat(policy.level(status, ago(500), NOW)).isEqualTo(QueueLevel.NONE);
        }
    }

    @Test
    void hoursInQueueIsWholeHoursAndNeverNegative() {
        assertThat(policy.hoursInQueue(SUBMITTED, NOW.minus(Duration.ofMinutes(150)), NOW)).isEqualTo(2);
        assertThat(policy.hoursInQueue(SUBMITTED, NOW.plus(Duration.ofHours(3)), NOW)).isZero();
    }
}
