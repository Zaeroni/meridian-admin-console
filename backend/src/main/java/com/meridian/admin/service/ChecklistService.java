package com.meridian.admin.service;

import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.CheckKey;
import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.ChecklistItem;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

/** Keeps an application's checklist in step with its data. The rules themselves live in ChecklistEvaluator. */
@Component
public class ChecklistService {

    /** Creates one item per known check, then fills in the automatic ones. */
    public void initialize(Application application, Instant now) {
        for (CheckKey key : CheckKey.values()) {
            application.getChecklist().add(new ChecklistItem(application, key));
        }
        refreshAutomatic(application, now);
    }

    /** Re-runs every automatic check against the pharmacy and documents as they are right now. */
    public void refreshAutomatic(Application application, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var outcomes = ChecklistEvaluator.evaluateAutomatic(
                application.getPharmacy(), application.getDocuments(), today);
        for (ChecklistItem item : application.getChecklist()) {
            var outcome = outcomes.get(item.getCheckKey());
            if (item.isAutomatic() && outcome != null) {
                item.record(outcome.result(), outcome.detail(), null, now);
            }
        }
    }

    /** Required checks that are not passing. Pending counts as not passing. */
    public List<ChecklistItem> blockers(Application application) {
        return application.getChecklist().stream()
                .filter(ChecklistItem::isRequired)
                .filter(c -> c.getResult() != CheckResult.PASS)
                .toList();
    }

    public static String describe(ChecklistItem item) {
        String detail = item.getDetail() == null || item.getDetail().isBlank() ? "not reviewed yet" : item.getDetail();
        return item.getCheckKey().name() + " is " + item.getResult() + ": " + detail;
    }
}
