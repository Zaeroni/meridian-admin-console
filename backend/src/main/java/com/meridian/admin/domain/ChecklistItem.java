package com.meridian.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checklist_items")
public class ChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id")
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_key", nullable = false)
    private CheckKey checkKey;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean required = true;

    @Column(nullable = false)
    private boolean automatic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckResult result = CheckResult.PENDING;

    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checked_by")
    private Reviewer checkedBy;

    @Column(name = "checked_at")
    private Instant checkedAt;

    protected ChecklistItem() {
    }

    public ChecklistItem(Application application, CheckKey key) {
        this.application = application;
        this.checkKey = key;
        this.label = key.label();
        this.automatic = key.automatic();
    }

    /** Records an outcome. checkedBy is null for automatic checks. */
    public void record(CheckResult result, String detail, Reviewer checkedBy, Instant at) {
        this.result = result;
        this.detail = detail;
        this.checkedBy = checkedBy;
        this.checkedAt = at;
    }

    public UUID getId() { return id; }
    public CheckKey getCheckKey() { return checkKey; }
    public String getLabel() { return label; }
    public boolean isRequired() { return required; }
    public boolean isAutomatic() { return automatic; }
    public CheckResult getResult() { return result; }
    public String getDetail() { return detail; }
    public Reviewer getCheckedBy() { return checkedBy; }
    public Instant getCheckedAt() { return checkedAt; }
}
