package com.meridian.admin.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A customer order placed on a pharmacy storefront. Seeded synthetic data for the overview. */
@Entity
@Table(name = "orders")
public class OrderRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pharmacy_id", nullable = false)
    private UUID pharmacyId;

    @Column(name = "total_cents", nullable = false)
    private int totalCents;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    protected OrderRecord() {
    }

    public OrderRecord(UUID pharmacyId, int totalCents, Instant placedAt) {
        this.pharmacyId = pharmacyId;
        this.totalCents = totalCents;
        this.placedAt = placedAt;
    }

    public UUID getId() { return id; }
    public UUID getPharmacyId() { return pharmacyId; }
    public int getTotalCents() { return totalCents; }
    public Instant getPlacedAt() { return placedAt; }
}
