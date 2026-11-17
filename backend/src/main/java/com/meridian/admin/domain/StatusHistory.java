package com.meridian.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** One row per status change. Rows are only ever inserted. */
@Entity
@Table(name = "status_history")
public class StatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ApplicationStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private Reviewer actor;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StatusHistory() {
    }

    public StatusHistory(UUID applicationId, ApplicationStatus from, ApplicationStatus to,
                         Reviewer actor, String reason, Instant at) {
        this.applicationId = applicationId;
        this.fromStatus = from;
        this.toStatus = to;
        this.actor = actor;
        this.reason = reason;
        this.createdAt = at;
    }

    public Long getId() { return id; }
    public UUID getApplicationId() { return applicationId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public Reviewer getActor() { return actor; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
