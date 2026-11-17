package com.meridian.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.meridian.admin.domain.Role;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowApiTest extends ApiTestBase {

    private String lead;
    private String reviewer;

    @BeforeEach
    void users() throws Exception {
        lead = tokenFor(Role.OPERATIONS_LEAD);
        reviewer = tokenFor(Role.REVIEWER);
    }

    // ---------- submission ----------

    @Test
    void newApplicationStartsSubmittedWithChecklistAndFirstHistoryRow() throws Exception {
        JsonNode app = createApplication(reviewer);

        assertThat(app.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(app.get("checklist")).hasSize(5);
        assertThat(app.get("history")).hasSize(1);
        assertThat(app.get("history").get(0).get("to").asText()).isEqualTo("SUBMITTED");
        assertThat(app.get("history").get(0).get("from").isNull()).isTrue();
    }

    @Test
    void invalidSubmissionIsRejectedWithFieldDetails() throws Exception {
        postAs(reviewer, "/api/applications", request(Map.of("state", "Texas", "ownerEmail", "nope", "businessName", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details[?(@ =~ /state.*/)]").exists())
                .andExpect(jsonPath("$.details[?(@ =~ /ownerEmail.*/)]").exists())
                .andExpect(jsonPath("$.details[?(@ =~ /businessName.*/)]").exists());
    }

    @Test
    void duplicateStorefrontSlugIsAConflict() throws Exception {
        String slug = "slug-" + UUID.randomUUID().toString().substring(0, 8);
        createApplication(reviewer, Map.of("storefrontSlug", slug));
        postAs(reviewer, "/api/applications", request(Map.of("storefrontSlug", slug)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("already in use")));
    }

    @Test
    void automaticChecksRunOnSubmission() throws Exception {
        JsonNode app = createApplication(reviewer, Map.of("licenseNumber", "BAD-1", "deliveryRadiusMiles", 80));
        assertThat(check(app, "LICENSE_FORMAT")).isEqualTo("FAIL");
        assertThat(check(app, "DELIVERY_RADIUS_SET")).isEqualTo("FAIL");
        assertThat(check(app, "DOCUMENTS_UPLOADED")).isEqualTo("FAIL");
        assertThat(check(app, "LICENSE_NOT_EXPIRED")).isEqualTo("PASS");
        assertThat(check(app, "BUSINESS_NAME_MATCH")).isEqualTo("PENDING");
    }

    // ---------- happy path and history ----------

    @Test
    void fullJourneyFromSubmittedToLiveAndBackThroughSuspension() throws Exception {
        String id = createApplication(lead).get("id").asText();
        uploadAllDocuments(lead, id);

        transition(lead, id, "IN_REVIEW", null).andExpect(status().isOk());
        setNameCheck(lead, id, "PASS").andExpect(status().isOk());
        transition(lead, id, "APPROVED", "License and documents verified").andExpect(status().isOk());
        transition(lead, id, "LIVE", null).andExpect(status().isOk());
        transition(lead, id, "SUSPENDED", "Customer complaint").andExpect(status().isOk());
        JsonNode finalState = read(transition(lead, id, "LIVE", "Complaint resolved").andExpect(status().isOk()));

        assertThat(finalState.get("status").asText()).isEqualTo("LIVE");

        JsonNode history = finalState.get("history");
        assertThat(history).hasSize(6);
        String[] expectedTo = {"SUBMITTED", "IN_REVIEW", "APPROVED", "LIVE", "SUSPENDED", "LIVE"};
        for (int i = 0; i < expectedTo.length; i++) {
            assertThat(history.get(i).get("to").asText()).isEqualTo(expectedTo[i]);
            assertThat(history.get(i).get("actor").asText()).isNotBlank();
        }
        assertThat(history.get(2).get("reason").asText()).isEqualTo("License and documents verified");
        assertThat(history.get(4).get("reason").asText()).isEqualTo("Customer complaint");
        assertThat(history.get(5).get("from").asText()).isEqualTo("SUSPENDED");
    }

    @Test
    void claimingAnApplicationAssignsTheReviewer() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        JsonNode claimed = read(transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk()));
        assertThat(claimed.get("assignedReviewer").asText()).startsWith("Test REVIEWER");
    }

    // ---------- reasons ----------

    @Test
    void approvingRejectingAndRequestingInfoAllRequireAReason() throws Exception {
        String id = readyForApproval(reviewer);

        for (String reason : new String[]{null, "", "   "}) {
            transition(reviewer, id, "APPROVED", reason).andExpect(status().isBadRequest());
            transition(reviewer, id, "REJECTED", reason).andExpect(status().isBadRequest());
            transition(reviewer, id, "NEEDS_MORE_INFO", reason).andExpect(status().isBadRequest());
        }
        getAs(reviewer, "/api/applications/" + id)
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.history", hasSize(2)));
    }

    @Test
    void suspendingAndReinstatingRequireAReason() throws Exception {
        String id = makeLive(lead, Map.of());
        transition(lead, id, "SUSPENDED", null).andExpect(status().isBadRequest());
        transition(lead, id, "SUSPENDED", "Complaint").andExpect(status().isOk());
        transition(lead, id, "LIVE", " ").andExpect(status().isBadRequest());
    }

    @Test
    void needsMoreInfoCarriesTheCommentAndCanReturnToReview() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());

        JsonNode asked = read(transition(reviewer, id, "NEEDS_MORE_INFO", "Please upload the owner ID")
                .andExpect(status().isOk()));
        assertThat(asked.get("status").asText()).isEqualTo("NEEDS_MORE_INFO");
        assertThat(asked.get("history").get(2).get("reason").asText()).isEqualTo("Please upload the owner ID");

        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    // ---------- illegal moves ----------

    @Test
    void illegalTransitionsAreConflictsAndChangeNothing() throws Exception {
        String id = createApplication(lead).get("id").asText();

        transition(lead, id, "APPROVED", "skipping review").andExpect(status().isConflict());
        transition(lead, id, "LIVE", null).andExpect(status().isConflict());
        transition(lead, id, "SUBMITTED", null).andExpect(status().isConflict());

        getAs(lead, "/api/applications/" + id)
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.history", hasSize(1)));
    }

    @Test
    void rejectedIsFinal() throws Exception {
        String id = createApplication(lead).get("id").asText();
        transition(lead, id, "IN_REVIEW", null).andExpect(status().isOk());
        transition(lead, id, "REJECTED", "Could not verify license").andExpect(status().isOk());

        for (String to : new String[]{"SUBMITTED", "IN_REVIEW", "APPROVED", "LIVE", "SUSPENDED"}) {
            transition(lead, id, to, "trying anyway").andExpect(status().isConflict());
        }
    }

    @Test
    void unknownStatusValueIsABadRequest() throws Exception {
        String id = createApplication(lead).get("id").asText();
        transition(lead, id, "FLYING", "x").andExpect(status().isBadRequest());
    }

    // ---------- the approval gate ----------

    @Test
    void cannotApproveWhileADocumentIsMissing() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());
        setNameCheck(reviewer, id, "PASS").andExpect(status().isOk());

        transition(reviewer, id, "APPROVED", "Looks fine").andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[?(@ =~ /DOCUMENTS_UPLOADED.*/)]").exists());

        getAs(reviewer, "/api/applications/" + id)
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.allRequiredChecksPass").value(false))
                .andExpect(jsonPath("$.history", hasSize(2)));
    }

    @Test
    void cannotApproveWhileAManualCheckIsStillPending() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        uploadAllDocuments(reviewer, id);
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());

        transition(reviewer, id, "APPROVED", "Looks fine").andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[?(@ =~ /BUSINESS_NAME_MATCH.*/)]").exists());
    }

    @Test
    void cannotApproveWhileAManualCheckIsFailing() throws Exception {
        String id = readyForApproval(reviewer);
        setNameCheck(reviewer, id, "FAIL").andExpect(status().isOk());

        transition(reviewer, id, "APPROVED", "Looks fine").andExpect(status().isConflict())
                .andExpect(jsonPath("$.details", hasItem(containsString("BUSINESS_NAME_MATCH is FAIL"))));
    }

    @Test
    void cannotApproveAnExpiredLicenseEvenIfEverythingElsePasses() throws Exception {
        String id = createApplication(reviewer, Map.of("licenseExpiration", LocalDate.now().minusDays(3).toString()))
                .get("id").asText();
        uploadAllDocuments(reviewer, id);
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());
        setNameCheck(reviewer, id, "PASS").andExpect(status().isOk());

        transition(reviewer, id, "APPROVED", "Looks fine").andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[?(@ =~ /LICENSE_NOT_EXPIRED.*/)]").exists());
    }

    @Test
    void approvalRechecksAutomaticChecksSoStaleResultsCannotSlipThrough() throws Exception {
        String id = readyForApproval(reviewer);
        getAs(reviewer, "/api/applications/" + id).andExpect(jsonPath("$.allRequiredChecksPass").value(true));

        // The license expires while the application sits In Review. The stored check still says PASS.
        var application = applications.findById(UUID.fromString(id)).orElseThrow();
        application.getPharmacy().setLicenseExpiration(LocalDate.now().minusDays(1));
        em.flush();

        transition(reviewer, id, "APPROVED", "Looks fine").andExpect(status().isConflict())
                .andExpect(jsonPath("$.details[?(@ =~ /LICENSE_NOT_EXPIRED.*/)]").exists());
    }

    @Test
    void approvalSucceedsOnceEveryRequiredCheckPasses() throws Exception {
        String id = readyForApproval(reviewer);
        getAs(reviewer, "/api/applications/" + id)
                .andExpect(jsonPath("$.allRequiredChecksPass").value(true))
                .andExpect(jsonPath("$.availableTransitions", hasItem("APPROVED")));
        transition(reviewer, id, "APPROVED", "All checks passed").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectionIsAllowedEvenWhenChecksFail() throws Exception {
        String id = createApplication(reviewer, Map.of("licenseNumber", "BAD")).get("id").asText();
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());
        transition(reviewer, id, "REJECTED", "License number is invalid").andExpect(status().isOk());
    }

    // ---------- checklist behaviour ----------

    @Test
    void uploadingTheLastDocumentFlipsTheDocumentCheckToPass() throws Exception {
        JsonNode app = createApplication(reviewer);
        String id = app.get("id").asText();
        assertThat(check(app, "DOCUMENTS_UPLOADED")).isEqualTo("FAIL");

        uploadAllDocuments(reviewer, id);
        JsonNode after = read(getAs(reviewer, "/api/applications/" + id));
        assertThat(check(after, "DOCUMENTS_UPLOADED")).isEqualTo("PASS");
        assertThat(after.get("documents")).hasSize(3);
    }

    @Test
    void manualChecksOnlyWorkWhileInReview() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        setNameCheck(reviewer, id, "PASS").andExpect(status().isConflict());
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());
        setNameCheck(reviewer, id, "PASS").andExpect(status().isOk());
    }

    @Test
    void automaticChecksCannotBeSetByHandAndManualOnesCannotBeSetToPending() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());

        putAs(reviewer, "/api/applications/" + id + "/checklist/LICENSE_FORMAT", Map.of("result", "PASS"))
                .andExpect(status().isBadRequest());
        setNameCheck(reviewer, id, "PENDING").andExpect(status().isBadRequest());
    }

    @Test
    void manualCheckRecordsWhoAndWhen() throws Exception {
        String id = createApplication(reviewer).get("id").asText();
        transition(reviewer, id, "IN_REVIEW", null).andExpect(status().isOk());
        JsonNode after = read(setNameCheck(reviewer, id, "PASS").andExpect(status().isOk()));

        for (JsonNode item : after.get("checklist")) {
            if (item.get("key").asText().equals("BUSINESS_NAME_MATCH")) {
                assertThat(item.get("checkedBy").asText()).startsWith("Test REVIEWER");
                assertThat(item.get("checkedAt").isNull()).isFalse();
                assertThat(item.get("detail").asText()).isEqualTo("Checked against the license");
            }
        }
    }

    @Test
    void checksAndDocumentsAreFrozenOnceADecisionIsMade() throws Exception {
        String id = readyForApproval(reviewer);
        transition(reviewer, id, "APPROVED", "Approved").andExpect(status().isOk());

        postAs(reviewer, "/api/applications/" + id + "/checks/run", Map.of()).andExpect(status().isConflict());
        postAs(reviewer, "/api/applications/" + id + "/documents", Map.of(
                "docType", "OWNER_ID", "fileName", "again.pdf", "contentType", "application/pdf", "sizeBytes", 10))
                .andExpect(status().isConflict());
    }

    // ---------- lookups ----------

    @Test
    void unknownAndMalformedIdsAreHandledCleanly() throws Exception {
        getAs(reviewer, "/api/applications/" + UUID.randomUUID()).andExpect(status().isNotFound());
        getAs(reviewer, "/api/applications/not-a-uuid").andExpect(status().isBadRequest());
        transition(reviewer, UUID.randomUUID().toString(), "IN_REVIEW", null).andExpect(status().isNotFound());
    }

    private static String check(JsonNode application, String key) {
        for (JsonNode item : application.get("checklist")) {
            if (item.get("key").asText().equals(key)) {
                return item.get("result").asText();
            }
        }
        throw new AssertionError("No checklist item " + key);
    }
}
