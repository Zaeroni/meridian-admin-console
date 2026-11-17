package com.meridian.admin.service;

import com.meridian.admin.domain.CheckKey;
import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.Document;
import com.meridian.admin.domain.DocumentType;
import com.meridian.admin.domain.Pharmacy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure logic for the automatic checks. No database, no clock of its own: the date is passed in,
 * which keeps these rules easy to test.
 */
public final class ChecklistEvaluator {

    public record Outcome(CheckResult result, String detail) {
    }

    /** Synthetic format used by this project: two letter state, a dash, then 6 to 8 digits. */
    private static final Pattern LICENSE_FORMAT = Pattern.compile("^([A-Z]{2})-\\d{6,8}$");

    public static final BigDecimal MAX_DELIVERY_RADIUS_MILES = new BigDecimal("50");

    private ChecklistEvaluator() {
    }

    public static Map<CheckKey, Outcome> evaluateAutomatic(Pharmacy pharmacy,
                                                           Collection<Document> documents,
                                                           LocalDate today) {
        Map<CheckKey, Outcome> results = new EnumMap<>(CheckKey.class);
        results.put(CheckKey.LICENSE_FORMAT, licenseFormat(pharmacy.getLicenseNumber(), pharmacy.getLicenseState()));
        results.put(CheckKey.LICENSE_NOT_EXPIRED, licenseNotExpired(pharmacy.getLicenseExpiration(), today));
        results.put(CheckKey.DOCUMENTS_UPLOADED, documentsUploaded(documents));
        results.put(CheckKey.DELIVERY_RADIUS_SET, deliveryRadius(pharmacy.getDeliveryRadiusMiles()));
        return results;
    }

    static Outcome licenseFormat(String licenseNumber, String licenseState) {
        if (licenseNumber == null || licenseNumber.isBlank()) {
            return fail("License number is missing");
        }
        var matcher = LICENSE_FORMAT.matcher(licenseNumber.trim());
        if (!matcher.matches()) {
            return fail("Expected format like TX-1234567 (state, dash, 6 to 8 digits), got " + licenseNumber);
        }
        if (!matcher.group(1).equals(licenseState)) {
            return fail("License prefix " + matcher.group(1) + " does not match license state " + licenseState);
        }
        return pass("Format is valid");
    }

    static Outcome licenseNotExpired(LocalDate expiration, LocalDate today) {
        if (expiration == null) {
            return fail("Expiration date is missing");
        }
        if (!expiration.isAfter(today)) {
            return fail("License expired on " + expiration);
        }
        long days = ChronoUnit.DAYS.between(today, expiration);
        return pass("Expires " + expiration + " (" + days + " days from now)");
    }

    static Outcome documentsUploaded(Collection<Document> documents) {
        Set<DocumentType> present = documents.stream()
                .map(Document::getDocType)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DocumentType.class)));
        var missing = Arrays.stream(DocumentType.values()).filter(t -> !present.contains(t)).toList();
        if (!missing.isEmpty()) {
            return fail("Missing: " + missing.stream().map(DocumentType::label).collect(Collectors.joining(", ")));
        }
        return pass("All " + DocumentType.values().length + " required documents are uploaded");
    }

    static Outcome deliveryRadius(BigDecimal radius) {
        if (radius == null) {
            return fail("Delivery radius has not been set");
        }
        if (radius.signum() <= 0) {
            return fail("Delivery radius must be greater than 0 miles");
        }
        if (radius.compareTo(MAX_DELIVERY_RADIUS_MILES) > 0) {
            return fail("Delivery radius " + radius + " miles is above the " + MAX_DELIVERY_RADIUS_MILES + " mile limit");
        }
        return pass(radius + " miles");
    }

    private static Outcome pass(String detail) {
        return new Outcome(CheckResult.PASS, detail);
    }

    private static Outcome fail(String detail) {
        return new Outcome(CheckResult.FAIL, detail);
    }
}
