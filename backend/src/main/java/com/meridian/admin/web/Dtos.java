package com.meridian.admin.web;

import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.domain.CheckKey;
import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.DocumentType;
import com.meridian.admin.domain.Role;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Request and response shapes for the API. Entities are never returned directly. */
public final class Dtos {

    private Dtos() {
    }

    // ---- Auth ----

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserDto(UUID id, String email, String fullName, Role role) {
    }

    public record LoginResponse(String token, Instant expiresAt, UserDto user) {
    }

    // ---- Requests ----

    public record CreateApplicationRequest(
            @NotBlank @Size(max = 200) String businessName,
            @NotBlank @Size(max = 120) String ownerName,
            @NotBlank @Email @Size(max = 255) String ownerEmail,
            @Size(max = 30) String phone,
            @NotBlank @Size(max = 200) String addressLine,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two letter state code like TX") String state,
            @NotBlank @Pattern(regexp = "^\\d{5}(-\\d{4})?$", message = "must be a ZIP code like 75001") String postalCode,
            @NotBlank @Size(max = 40) String licenseNumber,
            @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be a two letter state code like TX") String licenseState,
            @NotBlank @Size(max = 200) String licenseHolderName,
            @NotNull LocalDate licenseExpiration,
            @DecimalMin("0.0") @Digits(integer = 4, fraction = 1) BigDecimal deliveryRadiusMiles,
            @NotBlank @Size(max = 120) String storefrontName,
            @NotBlank @Size(min = 3, max = 80)
            @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase letters, digits and single dashes")
            String storefrontSlug) {
    }

    public record DocumentRequest(
            @NotNull DocumentType docType,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 100) String contentType,
            @Positive long sizeBytes) {
    }

    public record ManualCheckRequest(@NotNull CheckResult result, @Size(max = 500) String note) {
    }

    public record TransitionRequest(@NotNull ApplicationStatus to, @Size(max = 1000) String reason) {
    }

    // ---- Responses ----

    /** How urgent an open application's wait is. NONE for applications that are not waiting. */
    public enum QueueLevel { NONE, OK, WARN, CRITICAL }

    public record PharmacyDto(
            UUID id, String businessName, String ownerName, String ownerEmail, String phone,
            String addressLine, String city, String state, String postalCode,
            String licenseNumber, String licenseState, String licenseHolderName, LocalDate licenseExpiration,
            BigDecimal deliveryRadiusMiles, String storefrontName, String storefrontSlug) {
    }

    public record DocumentDto(UUID id, DocumentType docType, String fileName, String contentType,
                              long sizeBytes, Instant uploadedAt) {
    }

    public record ChecklistItemDto(CheckKey key, String label, boolean required, boolean automatic,
                                   CheckResult result, String detail, String checkedBy, Instant checkedAt) {
    }

    public record HistoryDto(Long id, ApplicationStatus from, ApplicationStatus to, String actor,
                             String reason, Instant at) {
    }

    public record ApplicationSummaryDto(
            UUID id, UUID pharmacyId, String businessName, String ownerName, String city, String state,
            ApplicationStatus status, String assignedReviewer, Instant submittedAt, Instant statusChangedAt,
            Long hoursInQueue, QueueLevel queueLevel) {
    }

    public record ApplicationDetailDto(
            UUID id, ApplicationStatus status, PharmacyDto pharmacy, String assignedReviewer,
            Instant submittedAt, Instant statusChangedAt, Long hoursInQueue, QueueLevel queueLevel,
            List<DocumentDto> documents, List<ChecklistItemDto> checklist, List<HistoryDto> history,
            List<ApplicationStatus> availableTransitions, boolean allRequiredChecksPass) {
    }

    public record PageDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    }

    // ---- Dashboard ----

    public record DailyOrdersDto(LocalDate day, long orders, long totalCents) {
    }

    public enum Severity { WARNING, CRITICAL }

    public record AttentionItemDto(UUID applicationId, String pharmacyName, ApplicationStatus status,
                                   String code, String message, Severity severity) {
    }

    public record OverviewDto(long totalPharmacies, Map<ApplicationStatus, Long> pharmaciesByStatus,
                              List<DailyOrdersDto> ordersPerDay, List<AttentionItemDto> needsAttention) {
    }
}
