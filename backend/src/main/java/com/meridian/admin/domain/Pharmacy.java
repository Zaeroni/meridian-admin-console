package com.meridian.admin.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pharmacies")
public class Pharmacy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    private String phone;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(name = "postal_code", nullable = false)
    private String postalCode;

    @Column(name = "license_number", nullable = false)
    private String licenseNumber;

    @Column(name = "license_state", nullable = false)
    private String licenseState;

    @Column(name = "license_holder_name", nullable = false)
    private String licenseHolderName;

    @Column(name = "license_expiration", nullable = false)
    private LocalDate licenseExpiration;

    @Column(name = "delivery_radius_miles")
    private BigDecimal deliveryRadiusMiles;

    @Column(name = "storefront_name", nullable = false)
    private String storefrontName;

    @Column(name = "storefront_slug", nullable = false, unique = true)
    private String storefrontSlug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Pharmacy() {
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String v) { this.businessName = v; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String v) { this.ownerName = v; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String v) { this.ownerEmail = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String v) { this.addressLine = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getState() { return state; }
    public void setState(String v) { this.state = v; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String v) { this.postalCode = v; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String v) { this.licenseNumber = v; }
    public String getLicenseState() { return licenseState; }
    public void setLicenseState(String v) { this.licenseState = v; }
    public String getLicenseHolderName() { return licenseHolderName; }
    public void setLicenseHolderName(String v) { this.licenseHolderName = v; }
    public LocalDate getLicenseExpiration() { return licenseExpiration; }
    public void setLicenseExpiration(LocalDate v) { this.licenseExpiration = v; }
    public BigDecimal getDeliveryRadiusMiles() { return deliveryRadiusMiles; }
    public void setDeliveryRadiusMiles(BigDecimal v) { this.deliveryRadiusMiles = v; }
    public String getStorefrontName() { return storefrontName; }
    public void setStorefrontName(String v) { this.storefrontName = v; }
    public String getStorefrontSlug() { return storefrontSlug; }
    public void setStorefrontSlug(String v) { this.storefrontSlug = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
