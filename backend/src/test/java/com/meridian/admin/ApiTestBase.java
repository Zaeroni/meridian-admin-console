package com.meridian.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.admin.domain.Reviewer;
import com.meridian.admin.domain.Role;
import com.meridian.admin.repo.ApplicationRepository;
import com.meridian.admin.repo.OrderRepository;
import com.meridian.admin.repo.PharmacyRepository;
import com.meridian.admin.repo.ReviewerRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup for API tests. They run against a real Postgres (see application-test.yml).
 * Each test is transactional, so everything it creates is rolled back afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestBase {

    protected static final String PASSWORD = "Password123!";
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected ReviewerRepository reviewers;
    @Autowired protected ApplicationRepository applications;
    @Autowired protected PharmacyRepository pharmacies;
    @Autowired protected OrderRepository orders;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected EntityManager em;

    // ---------- users and tokens ----------

    protected Reviewer newUser(Role role) {
        String email = role.name().toLowerCase() + COUNTER.incrementAndGet() + "@test.example";
        return reviewers.save(new Reviewer(email, encoder.encode(PASSWORD), "Test " + role, role));
    }

    protected String login(Reviewer user) throws Exception {
        var res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    /** Creates a user with the role and returns a valid token for them. */
    protected String tokenFor(Role role) throws Exception {
        return login(newUser(role));
    }

    // ---------- request helpers ----------

    protected ResultActions getAs(String token, String url) throws Exception {
        var req = get(url);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    protected ResultActions postAs(String token, String url, Object body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    protected ResultActions putAs(String token, String url, Object body) throws Exception {
        var req = put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    // ---------- domain helpers ----------

    /** A valid application request. Pass overrides to change individual fields. */
    protected Map<String, Object> request(Map<String, Object> overrides) {
        String n = String.valueOf(COUNTER.incrementAndGet());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessName", "Test Pharmacy " + n);
        body.put("ownerName", "Owner " + n);
        body.put("ownerEmail", "owner" + n + "@example.test");
        body.put("phone", "555-0100");
        body.put("addressLine", "100 Main Street");
        body.put("city", "Dallas");
        body.put("state", "TX");
        body.put("postalCode", "75201");
        body.put("licenseNumber", "TX-1234567");
        body.put("licenseState", "TX");
        body.put("licenseHolderName", "Test Pharmacy " + n);
        body.put("licenseExpiration", LocalDate.now().plusDays(365).toString());
        body.put("deliveryRadiusMiles", 10);
        body.put("storefrontName", "Test Pharmacy " + n);
        body.put("storefrontSlug", "test-pharmacy-" + n + "-" + UUID.randomUUID().toString().substring(0, 6));
        body.putAll(overrides);
        return body;
    }

    protected JsonNode createApplication(String token, Map<String, Object> overrides) throws Exception {
        return read(postAs(token, "/api/applications", request(overrides)).andExpect(status().isCreated()));
    }

    protected JsonNode createApplication(String token) throws Exception {
        return createApplication(token, Map.of());
    }

    protected ResultActions transition(String token, String id, String to, String reason) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to);
        if (reason != null) {
            body.put("reason", reason);
        }
        return postAs(token, "/api/applications/" + id + "/transitions", body);
    }

    protected void uploadAllDocuments(String token, String id) throws Exception {
        for (String type : new String[]{"PHARMACY_LICENSE", "BUSINESS_REGISTRATION", "OWNER_ID"}) {
            postAs(token, "/api/applications/" + id + "/documents", Map.of(
                    "docType", type, "fileName", type.toLowerCase() + ".pdf",
                    "contentType", "application/pdf", "sizeBytes", 50000)).andExpect(status().isCreated());
        }
    }

    protected ResultActions setNameCheck(String token, String id, String result) throws Exception {
        return putAs(token, "/api/applications/" + id + "/checklist/BUSINESS_NAME_MATCH",
                Map.of("result", result, "note", "Checked against the license"));
    }

    /** Submitted, all documents uploaded, claimed (In Review) and the manual name check passed. */
    protected String readyForApproval(String token) throws Exception {
        String id = createApplication(token).get("id").asText();
        uploadAllDocuments(token, id);
        transition(token, id, "IN_REVIEW", null).andExpect(status().isOk());
        setNameCheck(token, id, "PASS").andExpect(status().isOk());
        return id;
    }

    /** Takes a fresh application all the way to Live. Returns its id. */
    protected String makeLive(String leadToken, Map<String, Object> overrides) throws Exception {
        String id = createApplication(leadToken, overrides).get("id").asText();
        uploadAllDocuments(leadToken, id);
        transition(leadToken, id, "IN_REVIEW", null).andExpect(status().isOk());
        setNameCheck(leadToken, id, "PASS").andExpect(status().isOk());
        transition(leadToken, id, "APPROVED", "All checks passed").andExpect(status().isOk());
        transition(leadToken, id, "LIVE", null).andExpect(status().isOk());
        return id;
    }
}
