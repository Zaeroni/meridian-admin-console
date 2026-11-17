package com.meridian.admin.service;

import static com.meridian.admin.domain.ApplicationStatus.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.domain.Role;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransitionRulesTest {

    @Test
    void onlyTheDocumentedTransitionsExist() {
        Set<String> allowed = Set.of(
                "SUBMITTED>IN_REVIEW", "IN_REVIEW>NEEDS_MORE_INFO", "NEEDS_MORE_INFO>IN_REVIEW",
                "IN_REVIEW>APPROVED", "IN_REVIEW>REJECTED", "APPROVED>LIVE", "LIVE>SUSPENDED", "SUSPENDED>LIVE");

        for (ApplicationStatus from : ApplicationStatus.values()) {
            for (ApplicationStatus to : ApplicationStatus.values()) {
                boolean exists = TransitionRules.find(from, to).isPresent();
                assertThat(exists)
                        .as("%s -> %s", from, to)
                        .isEqualTo(allowed.contains(from + ">" + to));
            }
        }
    }

    @Test
    void rejectedIsTerminal() {
        assertThat(TransitionRules.nextStatuses(REJECTED)).isEmpty();
    }

    @Test
    void reasonIsRequiredExactlyWhereTheBriefSaysSo() {
        assertThat(TransitionRules.find(IN_REVIEW, APPROVED).orElseThrow().reasonRequired()).isTrue();
        assertThat(TransitionRules.find(IN_REVIEW, REJECTED).orElseThrow().reasonRequired()).isTrue();
        assertThat(TransitionRules.find(IN_REVIEW, NEEDS_MORE_INFO).orElseThrow().reasonRequired()).isTrue();
        assertThat(TransitionRules.find(LIVE, SUSPENDED).orElseThrow().reasonRequired()).isTrue();
        assertThat(TransitionRules.find(SUSPENDED, LIVE).orElseThrow().reasonRequired()).isTrue();
        assertThat(TransitionRules.find(SUBMITTED, IN_REVIEW).orElseThrow().reasonRequired()).isFalse();
    }

    @Test
    void onlyApprovalRequiresPassingChecks() {
        for (var rule : TransitionRules.all()) {
            assertThat(rule.requiresPassingChecks())
                    .as("%s -> %s", rule.from(), rule.to())
                    .isEqualTo(rule.to() == APPROVED);
        }
    }

    @Test
    void readOnlyCanNeverMoveAnything() {
        for (var status : ApplicationStatus.values()) {
            assertThat(TransitionRules.nextStatusesFor(status, Role.READ_ONLY)).isEmpty();
        }
    }

    @Test
    void reviewersCannotGoLiveSuspendOrReinstate() {
        assertThat(TransitionRules.nextStatusesFor(APPROVED, Role.REVIEWER)).isEmpty();
        assertThat(TransitionRules.nextStatusesFor(LIVE, Role.REVIEWER)).isEmpty();
        assertThat(TransitionRules.nextStatusesFor(SUSPENDED, Role.REVIEWER)).isEmpty();
        assertThat(TransitionRules.nextStatusesFor(IN_REVIEW, Role.REVIEWER))
                .containsExactlyInAnyOrder(NEEDS_MORE_INFO, APPROVED, REJECTED);
    }

    @Test
    void operationsLeadCanDoEverythingAReviewerCan() {
        for (var status : ApplicationStatus.values()) {
            Set<ApplicationStatus> reviewer = TransitionRules.nextStatusesFor(status, Role.REVIEWER);
            Set<ApplicationStatus> lead = TransitionRules.nextStatusesFor(status, Role.OPERATIONS_LEAD);
            assertThat(lead).containsAll(reviewer);
            assertThat(lead).isEqualTo(TransitionRules.nextStatuses(status));
        }
        assertThat(EnumSet.allOf(Role.class)).hasSize(3);
    }
}
