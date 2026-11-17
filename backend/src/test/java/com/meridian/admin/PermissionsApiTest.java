package com.meridian.admin;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meridian.admin.domain.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Permissions are enforced by the server. These tests call the API directly, with no UI involved. */
class PermissionsApiTest extends ApiTestBase {

    @Test
    void readOnlyCanReadEverything() throws Exception {
        String lead = tokenFor(Role.OPERATIONS_LEAD);
        String id = createApplication(lead).get("id").asText();
        String readOnly = tokenFor(Role.READ_ONLY);

        getAs(readOnly, "/api/applications").andExpect(status().isOk());
        getAs(readOnly, "/api/applications/" + id).andExpect(status().isOk());
        getAs(readOnly, "/api/dashboard/overview").andExpect(status().isOk());
    }

    @Test
    void readOnlyCannotWriteAnything() throws Exception {
        String lead = tokenFor(Role.OPERATIONS_LEAD);
        String id = createApplication(lead).get("id").asText();
        String readOnly = tokenFor(Role.READ_ONLY);

        postAs(readOnly, "/api/applications", request(Map.of())).andExpect(status().isForbidden());
        transition(readOnly, id, "IN_REVIEW", null).andExpect(status().isForbidden());
        postAs(readOnly, "/api/applications/" + id + "/documents", Map.of(
                "docType", "OWNER_ID", "fileName", "id.pdf", "contentType", "application/pdf", "sizeBytes", 10))
                .andExpect(status().isForbidden());
        postAs(readOnly, "/api/applications/" + id + "/checks/run", Map.of()).andExpect(status().isForbidden());
        setNameCheck(readOnly, id, "PASS").andExpect(status().isForbidden());

        // And nothing changed.
        getAs(lead, "/api/applications/" + id).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void reviewerCanSubmitClaimReviewApproveAndReject() throws Exception {
        String reviewer = tokenFor(Role.REVIEWER);
        String id = readyForApproval(reviewer);
        transition(reviewer, id, "APPROVED", "Everything checks out").andExpect(status().isOk());

        String other = createApplication(reviewer).get("id").asText();
        transition(reviewer, other, "IN_REVIEW", null).andExpect(status().isOk());
        transition(reviewer, other, "REJECTED", "License cannot be verified").andExpect(status().isOk());
    }

    @Test
    void reviewerCannotGoLive() throws Exception {
        String reviewer = tokenFor(Role.REVIEWER);
        String id = readyForApproval(reviewer);
        transition(reviewer, id, "APPROVED", "Approved").andExpect(status().isOk());

        transition(reviewer, id, "LIVE", null).andExpect(status().isForbidden());
        getAs(reviewer, "/api/applications/" + id).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void reviewerCannotSuspendOrReinstate() throws Exception {
        String lead = tokenFor(Role.OPERATIONS_LEAD);
        String reviewer = tokenFor(Role.REVIEWER);
        String id = makeLive(lead, Map.of());

        transition(reviewer, id, "SUSPENDED", "Complaint").andExpect(status().isForbidden());
        transition(lead, id, "SUSPENDED", "Complaint").andExpect(status().isOk());
        transition(reviewer, id, "LIVE", "Cleared").andExpect(status().isForbidden());
        transition(lead, id, "LIVE", "Cleared").andExpect(status().isOk());
    }

    @Test
    void meReturnsTheCallersRole() throws Exception {
        String reviewer = tokenFor(Role.REVIEWER);
        getAs(reviewer, "/api/auth/me").andExpect(jsonPath("$.role").value("REVIEWER"));
    }

    @Test
    void availableTransitionsReflectTheCallersRole() throws Exception {
        String lead = tokenFor(Role.OPERATIONS_LEAD);
        String reviewer = tokenFor(Role.REVIEWER);
        String readOnly = tokenFor(Role.READ_ONLY);
        String id = makeLive(lead, Map.of());

        getAs(lead, "/api/applications/" + id).andExpect(jsonPath("$.availableTransitions[0]").value("SUSPENDED"));
        getAs(reviewer, "/api/applications/" + id).andExpect(jsonPath("$.availableTransitions").isEmpty());
        getAs(readOnly, "/api/applications/" + id).andExpect(jsonPath("$.availableTransitions").isEmpty());
    }
}
