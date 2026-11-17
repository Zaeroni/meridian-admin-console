package com.meridian.admin.service;

import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.domain.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.meridian.admin.domain.ApplicationStatus.*;
import static com.meridian.admin.domain.Role.OPERATIONS_LEAD;
import static com.meridian.admin.domain.Role.REVIEWER;

/**
 * The onboarding state machine, written as data so it can be read, tested and drawn from one place.
 * Anything not listed here is not allowed.
 */
public final class TransitionRules {

    public record Rule(ApplicationStatus from,
                       ApplicationStatus to,
                       Set<Role> allowedRoles,
                       boolean reasonRequired,
                       boolean requiresPassingChecks) {
    }

    private static final Set<Role> REVIEW_STAFF = EnumSet.of(REVIEWER, OPERATIONS_LEAD);
    private static final Set<Role> LEADS_ONLY = EnumSet.of(OPERATIONS_LEAD);

    private static final List<Rule> RULES = List.of(
            new Rule(SUBMITTED, IN_REVIEW, REVIEW_STAFF, false, false),
            new Rule(IN_REVIEW, NEEDS_MORE_INFO, REVIEW_STAFF, true, false),
            new Rule(NEEDS_MORE_INFO, IN_REVIEW, REVIEW_STAFF, false, false),
            new Rule(IN_REVIEW, APPROVED, REVIEW_STAFF, true, true),
            new Rule(IN_REVIEW, REJECTED, REVIEW_STAFF, true, false),
            new Rule(APPROVED, LIVE, LEADS_ONLY, false, false),
            new Rule(LIVE, SUSPENDED, LEADS_ONLY, true, false),
            new Rule(SUSPENDED, LIVE, LEADS_ONLY, true, false)
    );

    private TransitionRules() {
    }

    public static Optional<Rule> find(ApplicationStatus from, ApplicationStatus to) {
        return RULES.stream().filter(r -> r.from() == from && r.to() == to).findFirst();
    }

    /** Statuses reachable from the given one, regardless of who is asking. */
    public static Set<ApplicationStatus> nextStatuses(ApplicationStatus from) {
        return RULES.stream()
                .filter(r -> r.from() == from)
                .map(Rule::to)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApplicationStatus.class)));
    }

    /** Statuses reachable from the given one that this role is allowed to trigger. */
    public static Set<ApplicationStatus> nextStatusesFor(ApplicationStatus from, Role role) {
        return RULES.stream()
                .filter(r -> r.from() == from && r.allowedRoles().contains(role))
                .map(Rule::to)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ApplicationStatus.class)));
    }

    public static List<Rule> all() {
        return RULES;
    }
}
