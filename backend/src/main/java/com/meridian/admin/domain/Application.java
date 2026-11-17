package com.meridian.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id")
    private Pharmacy pharmacy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_reviewer_id")
    private Reviewer assignedReviewer;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("checkKey ASC")
    private List<ChecklistItem> checklist = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("uploadedAt ASC")
    private List<Document> documents = new ArrayList<>();

    protected Application() {
    }

    public Application(Pharmacy pharmacy, Instant submittedAt) {
        this.pharmacy = pharmacy;
        this.status = ApplicationStatus.SUBMITTED;
        this.submittedAt = submittedAt;
        this.statusChangedAt = submittedAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    /** Moves to a new status. The caller is responsible for having validated the transition. */
    public void moveTo(ApplicationStatus next, Instant at) {
        this.status = next;
        this.statusChangedAt = at;
    }

    public void assignTo(Reviewer reviewer) {
        this.assignedReviewer = reviewer;
    }

    public UUID getId() { return id; }
    public Pharmacy getPharmacy() { return pharmacy; }
    public ApplicationStatus getStatus() { return status; }
    public Reviewer getAssignedReviewer() { return assignedReviewer; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<ChecklistItem> getChecklist() { return checklist; }
    public List<Document> getDocuments() { return documents; }
}
