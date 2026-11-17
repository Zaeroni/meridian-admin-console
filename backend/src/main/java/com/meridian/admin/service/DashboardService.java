package com.meridian.admin.service;

import com.meridian.admin.config.QueueProperties;
import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.repo.ApplicationRepository;
import com.meridian.admin.repo.OrderRepository;
import com.meridian.admin.web.Dtos.AttentionItemDto;
import com.meridian.admin.web.Dtos.DailyOrdersDto;
import com.meridian.admin.web.Dtos.OverviewDto;
import com.meridian.admin.web.Dtos.Severity;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    static final int ORDER_DAYS = 14;
    static final int ATTENTION_LIMIT = 25;

    private final ApplicationRepository applications;
    private final OrderRepository orders;
    private final QueueProperties queue;
    private final Clock clock;

    public DashboardService(ApplicationRepository applications, OrderRepository orders,
                            QueueProperties queue, Clock clock) {
        this.applications = applications;
        this.orders = orders;
        this.queue = queue;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OverviewDto overview() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        for (var s : ApplicationStatus.values()) {
            byStatus.put(s, 0L);
        }
        applications.countByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getTotal()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        return new OverviewDto(total, byStatus, ordersPerDay(today), needsAttention(now, today));
    }

    private List<DailyOrdersDto> ordersPerDay(LocalDate today) {
        LocalDate first = today.minusDays(ORDER_DAYS - 1L);
        Instant since = first.atStartOfDay().toInstant(ZoneOffset.UTC);
        Map<LocalDate, long[]> found = new HashMap<>();
        for (Object[] row : orders.countPerDaySince(since)) {
            LocalDate day = row[0] instanceof Date d ? d.toLocalDate() : LocalDate.parse(row[0].toString());
            found.put(day, new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        List<DailyOrdersDto> result = new ArrayList<>();
        for (int i = 0; i < ORDER_DAYS; i++) {
            LocalDate day = first.plusDays(i);
            long[] v = found.getOrDefault(day, new long[]{0, 0});
            result.add(new DailyOrdersDto(day, v[0], v[1]));
        }
        return result;
    }

    private List<AttentionItemDto> needsAttention(Instant now, LocalDate today) {
        List<AttentionItemDto> items = new ArrayList<>();

        // Review and in-review waits that have gone past the limit.
        var reviewCutoff = now.minus(Duration.ofHours(queue.attentionHoursReview()));
        for (Application a : applications.findByStatusInAndStatusChangedAtBefore(
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.IN_REVIEW), reviewCutoff)) {
            items.add(item(a, "STALE_IN_QUEUE", Severity.CRITICAL,
                    "Waiting " + waited(a, now) + " in " + label(a.getStatus())));
        }

        // Waiting on the applicant for too long.
        var infoCutoff = now.minus(Duration.ofDays(queue.attentionDaysMoreInfo()));
        for (Application a : applications.findByStatusInAndStatusChangedAtBefore(
                List.of(ApplicationStatus.NEEDS_MORE_INFO), infoCutoff)) {
            items.add(item(a, "WAITING_ON_APPLICANT", Severity.WARNING,
                    "Waiting " + waited(a, now) + " for the applicant to respond"));
        }

        // Live pharmacies whose license has expired or is about to.
        var warnBy = today.plusDays(queue.licenseExpiryWarningDays());
        for (Application a : applications.findByStatusWithLicenseExpiringBy(ApplicationStatus.LIVE, warnBy)) {
            LocalDate expires = a.getPharmacy().getLicenseExpiration();
            if (!expires.isAfter(today)) {
                items.add(item(a, "LICENSE_EXPIRED", Severity.CRITICAL, "License expired on " + expires));
            } else {
                long days = ChronoUnit.DAYS.between(today, expires);
                items.add(item(a, "LICENSE_EXPIRING", Severity.WARNING,
                        "License expires in " + days + " day" + (days == 1 ? "" : "s") + " (" + expires + ")"));
            }
        }

        // Suspended pharmacies need a decision.
        for (Application a : applications.findByStatus(ApplicationStatus.SUSPENDED)) {
            items.add(item(a, "SUSPENDED", Severity.WARNING,
                    "Suspended for " + waited(a, now) + ", needs a decision"));
        }

        items.sort(Comparator.comparing(AttentionItemDto::severity).reversed()
                .thenComparing(AttentionItemDto::pharmacyName));
        return items.size() > ATTENTION_LIMIT ? items.subList(0, ATTENTION_LIMIT) : items;
    }

    private static AttentionItemDto item(Application a, String code, Severity severity, String message) {
        return new AttentionItemDto(a.getId(), a.getPharmacy().getBusinessName(), a.getStatus(),
                code, message, severity);
    }

    private static String waited(Application a, Instant now) {
        long hours = Duration.between(a.getStatusChangedAt(), now).toHours();
        return hours >= 48 ? (hours / 24) + " days" : hours + " hours";
    }

    private static String label(ApplicationStatus s) {
        return switch (s) {
            case SUBMITTED -> "Submitted";
            case IN_REVIEW -> "In Review";
            case NEEDS_MORE_INFO -> "Needs More Info";
            default -> s.name();
        };
    }
}
