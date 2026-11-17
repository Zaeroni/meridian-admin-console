package com.meridian.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.admin.domain.OrderRecord;
import com.meridian.admin.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardApiTest extends ApiTestBase {

    private String lead;

    @BeforeEach
    void user() throws Exception {
        lead = tokenFor(Role.OPERATIONS_LEAD);
    }

    @Test
    void overviewCountsPharmaciesByStatusIncludingZeros() throws Exception {
        createApplication(lead);
        createApplication(lead);
        String inReview = createApplication(lead).get("id").asText();
        transition(lead, inReview, "IN_REVIEW", null).andExpect(status().isOk());
        makeLive(lead, Map.of());

        getAs(lead, "/api/dashboard/overview").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPharmacies").value(4))
                .andExpect(jsonPath("$.pharmaciesByStatus.SUBMITTED").value(2))
                .andExpect(jsonPath("$.pharmaciesByStatus.IN_REVIEW").value(1))
                .andExpect(jsonPath("$.pharmaciesByStatus.LIVE").value(1))
                .andExpect(jsonPath("$.pharmaciesByStatus.SUSPENDED").value(0))
                .andExpect(jsonPath("$.pharmaciesByStatus.REJECTED").value(0));
    }

    @Test
    void ordersPerDayCoversFourteenDaysAndFillsGapsWithZero() throws Exception {
        UUID pharmacy = pharmacies.findAll().stream().findFirst()
                .orElseGet(() -> {
                    try {
                        String id = createApplication(lead).get("id").asText();
                        return applications.findById(UUID.fromString(id)).orElseThrow().getPharmacy();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).getId();

        Instant noonToday = LocalDate.now(java.time.ZoneOffset.UTC).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC);
        Instant threeDaysAgo = noonToday.minus(Duration.ofDays(3));
        orders.save(new OrderRecord(pharmacy, 2000, threeDaysAgo));
        orders.save(new OrderRecord(pharmacy, 3000, threeDaysAgo.plusSeconds(60)));
        orders.save(new OrderRecord(pharmacy, 1000, Instant.now().minus(Duration.ofDays(40)))); // outside window
        em.flush();

        JsonNode overview = read(getAs(lead, "/api/dashboard/overview").andExpect(status().isOk()));
        JsonNode days = overview.get("ordersPerDay");
        assertThat(days).hasSize(14);

        String target = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(3).toString();
        long total = 0;
        for (JsonNode d : days) {
            total += d.get("orders").asLong();
            if (d.get("day").asText().equals(target)) {
                assertThat(d.get("orders").asLong()).isEqualTo(2);
                assertThat(d.get("totalCents").asLong()).isEqualTo(5000);
            } else {
                assertThat(d.get("orders").asLong()).isZero();
            }
        }
        assertThat(total).isEqualTo(2);
        // Oldest first, today last.
        assertThat(days.get(13).get("day").asText()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC).toString());
    }

    @Test
    void staleApplicationsAreFlaggedButFreshOnesAreNot() throws Exception {
        String stale = createApplication(lead, Map.of("businessName", "Stale Submit")).get("id").asText();
        createApplication(lead, Map.of("businessName", "Fresh Submit"));
        var app = applications.findById(UUID.fromString(stale)).orElseThrow();
        app.moveTo(app.getStatus(), Instant.now().minus(Duration.ofHours(60)));
        em.flush();

        getAs(lead, "/api/dashboard/overview")
                .andExpect(jsonPath("$.needsAttention", hasSize(1)))
                .andExpect(jsonPath("$.needsAttention[0].pharmacyName").value("Stale Submit"))
                .andExpect(jsonPath("$.needsAttention[0].code").value("STALE_IN_QUEUE"))
                .andExpect(jsonPath("$.needsAttention[0].severity").value("CRITICAL"));
    }

    @Test
    void waitingOnApplicantFlagsOnlyAfterTheLongerLimit() throws Exception {
        String recent = createApplication(lead, Map.of("businessName", "Info Recent")).get("id").asText();
        String old = createApplication(lead, Map.of("businessName", "Info Old")).get("id").asText();
        for (String id : new String[]{recent, old}) {
            transition(lead, id, "IN_REVIEW", null).andExpect(status().isOk());
            transition(lead, id, "NEEDS_MORE_INFO", "Need documents").andExpect(status().isOk());
        }
        var app = applications.findById(UUID.fromString(old)).orElseThrow();
        app.moveTo(app.getStatus(), Instant.now().minus(Duration.ofDays(9)));
        var app2 = applications.findById(UUID.fromString(recent)).orElseThrow();
        app2.moveTo(app2.getStatus(), Instant.now().minus(Duration.ofDays(3)));
        em.flush();

        getAs(lead, "/api/dashboard/overview")
                .andExpect(jsonPath("$.needsAttention", hasSize(1)))
                .andExpect(jsonPath("$.needsAttention[0].pharmacyName").value("Info Old"))
                .andExpect(jsonPath("$.needsAttention[0].code").value("WAITING_ON_APPLICANT"));
    }

    @Test
    void livePharmaciesWithExpiringOrExpiredLicensesAreFlagged() throws Exception {
        makeLive(lead, Map.of("businessName", "Healthy Live"));
        makeLive(lead, Map.of("businessName", "Expiring Soon",
                "licenseExpiration", LocalDate.now().plusDays(10).toString()));
        String expired = makeLive(lead, Map.of("businessName", "Already Expired"));
        var app = applications.findById(UUID.fromString(expired)).orElseThrow();
        app.getPharmacy().setLicenseExpiration(LocalDate.now().minusDays(2));
        em.flush();

        JsonNode items = read(getAs(lead, "/api/dashboard/overview").andExpect(status().isOk())).get("needsAttention");
        assertThat(items).hasSize(2);
        // Critical first.
        assertThat(items.get(0).get("pharmacyName").asText()).isEqualTo("Already Expired");
        assertThat(items.get(0).get("code").asText()).isEqualTo("LICENSE_EXPIRED");
        assertThat(items.get(0).get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(items.get(1).get("pharmacyName").asText()).isEqualTo("Expiring Soon");
        assertThat(items.get(1).get("code").asText()).isEqualTo("LICENSE_EXPIRING");
        assertThat(items.get(1).get("message").asText()).contains("10 days");
    }

    @Test
    void suspendedPharmaciesNeedADecision() throws Exception {
        String id = makeLive(lead, Map.of("businessName", "Under Investigation"));
        transition(lead, id, "SUSPENDED", "Complaint").andExpect(status().isOk());

        getAs(lead, "/api/dashboard/overview")
                .andExpect(jsonPath("$.needsAttention", hasSize(1)))
                .andExpect(jsonPath("$.needsAttention[0].code").value("SUSPENDED"));
    }
}
