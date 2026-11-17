package com.meridian.admin.service;

import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.ChecklistItem;
import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.Document;
import com.meridian.admin.domain.Pharmacy;
import com.meridian.admin.domain.Reviewer;
import com.meridian.admin.domain.Role;
import com.meridian.admin.domain.StatusHistory;
import com.meridian.admin.web.Dtos.*;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ApplicationMapper {

    private final QueuePolicy queuePolicy;
    private final Clock clock;

    public ApplicationMapper(QueuePolicy queuePolicy, Clock clock) {
        this.queuePolicy = queuePolicy;
        this.clock = clock;
    }

    public ApplicationSummaryDto summary(Application a) {
        Pharmacy p = a.getPharmacy();
        var now = clock.instant();
        return new ApplicationSummaryDto(
                a.getId(), p.getId(), p.getBusinessName(), p.getOwnerName(), p.getCity(), p.getState(),
                a.getStatus(), name(a.getAssignedReviewer()), a.getSubmittedAt(), a.getStatusChangedAt(),
                queuePolicy.hoursInQueue(a.getStatus(), a.getStatusChangedAt(), now),
                queuePolicy.level(a.getStatus(), a.getStatusChangedAt(), now));
    }

    public ApplicationDetailDto detail(Application a, List<StatusHistory> history, Role viewerRole) {
        var now = clock.instant();
        boolean allPass = a.getChecklist().stream()
                .filter(ChecklistItem::isRequired)
                .allMatch(c -> c.getResult() == CheckResult.PASS);
        return new ApplicationDetailDto(
                a.getId(), a.getStatus(), pharmacy(a.getPharmacy()), name(a.getAssignedReviewer()),
                a.getSubmittedAt(), a.getStatusChangedAt(),
                queuePolicy.hoursInQueue(a.getStatus(), a.getStatusChangedAt(), now),
                queuePolicy.level(a.getStatus(), a.getStatusChangedAt(), now),
                a.getDocuments().stream().map(this::document).toList(),
                a.getChecklist().stream()
                        .sorted(Comparator.comparing(ChecklistItem::getCheckKey)) // enum order = display order
                        .map(this::checklistItem).toList(),
                history.stream().map(this::history).toList(),
                TransitionRules.nextStatusesFor(a.getStatus(), viewerRole).stream()
                        .sorted(Comparator.naturalOrder()).toList(),
                allPass);
    }

    private PharmacyDto pharmacy(Pharmacy p) {
        return new PharmacyDto(p.getId(), p.getBusinessName(), p.getOwnerName(), p.getOwnerEmail(), p.getPhone(),
                p.getAddressLine(), p.getCity(), p.getState(), p.getPostalCode(),
                p.getLicenseNumber(), p.getLicenseState(), p.getLicenseHolderName(), p.getLicenseExpiration(),
                p.getDeliveryRadiusMiles(), p.getStorefrontName(), p.getStorefrontSlug());
    }

    private DocumentDto document(Document d) {
        return new DocumentDto(d.getId(), d.getDocType(), d.getFileName(), d.getContentType(),
                d.getSizeBytes(), d.getUploadedAt());
    }

    private ChecklistItemDto checklistItem(ChecklistItem c) {
        return new ChecklistItemDto(c.getCheckKey(), c.getLabel(), c.isRequired(), c.isAutomatic(),
                c.getResult(), c.getDetail(), name(c.getCheckedBy()), c.getCheckedAt());
    }

    private HistoryDto history(StatusHistory h) {
        return new HistoryDto(h.getId(), h.getFromStatus(), h.getToStatus(), name(h.getActor()),
                h.getReason(), h.getCreatedAt());
    }

    private static String name(Reviewer r) {
        return r == null ? null : r.getFullName();
    }
}
