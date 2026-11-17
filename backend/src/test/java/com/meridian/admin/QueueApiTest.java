package com.meridian.admin;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meridian.admin.domain.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueApiTest extends ApiTestBase {

    private String lead;

    @BeforeEach
    void user() throws Exception {
        lead = tokenFor(Role.OPERATIONS_LEAD);
    }

    /** Creates an application and pretends its current status began the given number of hours ago. */
    private String appWaiting(Map<String, Object> overrides, long hoursAgo) throws Exception {
        String id = createApplication(lead, overrides).get("id").asText();
        backdate(id, hoursAgo);
        return id;
    }

    private void backdate(String id, long hoursAgo) {
        var app = applications.findById(UUID.fromString(id)).orElseThrow();
        app.moveTo(app.getStatus(), Instant.now().minus(Duration.ofHours(hoursAgo)));
        em.flush();
    }

    @Test
    void filtersByStatus() throws Exception {
        String a = createApplication(lead, Map.of("businessName", "Filter Alpha")).get("id").asText();
        createApplication(lead, Map.of("businessName", "Filter Bravo"));
        transition(lead, a, "IN_REVIEW", null).andExpect(status().isOk());

        getAs(lead, "/api/applications?status=IN_REVIEW").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].businessName").value("Filter Alpha"));

        getAs(lead, "/api/applications?status=SUBMITTED&status=IN_REVIEW")
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs(lead, "/api/applications").andExpect(jsonPath("$.totalElements").value(2));
        getAs(lead, "/api/applications?status=NOPE").andExpect(status().isBadRequest());
    }

    @Test
    void searchMatchesNameOwnerLicenseAndCityCaseInsensitively() throws Exception {
        createApplication(lead, Map.of("businessName", "Magnolia Corner Drug", "ownerName", "Ada Quill",
                "licenseNumber", "TX-7654321", "city", "Waxahachie"));
        createApplication(lead, Map.of("businessName", "Unrelated Rx"));

        for (String term : new String[]{"magnolia", "QUILL", "7654321", "waxa"}) {
            getAs(lead, "/api/applications?q=" + term)
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.items[0].businessName").value("Magnolia Corner Drug"));
        }
        getAs(lead, "/api/applications?q=zzzzzz").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void percentAndUnderscoreInSearchAreNotWildcards() throws Exception {
        createApplication(lead, Map.of("businessName", "Plain Name One"));
        createApplication(lead, Map.of("businessName", "Plain Name Two"));

        getAs(lead, "/api/applications?q=%25").andExpect(jsonPath("$.totalElements").value(0));
        getAs(lead, "/api/applications?q=_").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortsByTimeInQueueLongestWaitFirstByDefault() throws Exception {
        appWaiting(Map.of("businessName", "Waited 5h"), 5);
        appWaiting(Map.of("businessName", "Waited 70h"), 70);
        appWaiting(Map.of("businessName", "Waited 30h"), 30);

        getAs(lead, "/api/applications").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].businessName", contains("Waited 70h", "Waited 30h", "Waited 5h")));
        getAs(lead, "/api/applications?sort=timeInQueue&dir=asc")
                .andExpect(jsonPath("$.items[*].businessName", contains("Waited 5h", "Waited 30h", "Waited 70h")));
    }

    @Test
    void sortsByNameSubmittedDateAndStatus() throws Exception {
        createApplication(lead, Map.of("businessName", "Bravo"));
        String charlie = createApplication(lead, Map.of("businessName", "Charlie")).get("id").asText();
        createApplication(lead, Map.of("businessName", "Alpha"));
        transition(lead, charlie, "IN_REVIEW", null).andExpect(status().isOk());

        getAs(lead, "/api/applications?sort=businessName&dir=asc")
                .andExpect(jsonPath("$.items[*].businessName", contains("Alpha", "Bravo", "Charlie")));
        getAs(lead, "/api/applications?sort=businessName&dir=desc")
                .andExpect(jsonPath("$.items[*].businessName", contains("Charlie", "Bravo", "Alpha")));
        getAs(lead, "/api/applications?sort=status&dir=asc")
                .andExpect(jsonPath("$.items[0].businessName").value("Charlie")); // IN_REVIEW sorts before SUBMITTED
        getAs(lead, "/api/applications?sort=submittedAt&dir=desc").andExpect(status().isOk());
    }

    @Test
    void rejectsUnknownSortsDirectionsAndPageSizes() throws Exception {
        getAs(lead, "/api/applications?sort=password").andExpect(status().isBadRequest());
        getAs(lead, "/api/applications?dir=sideways").andExpect(status().isBadRequest());
        getAs(lead, "/api/applications?size=0").andExpect(status().isBadRequest());
        getAs(lead, "/api/applications?size=1000").andExpect(status().isBadRequest());
        getAs(lead, "/api/applications?page=-1").andExpect(status().isBadRequest());
    }

    @Test
    void paginates() throws Exception {
        for (int i = 1; i <= 5; i++) {
            createApplication(lead, Map.of("businessName", "Page Item " + i));
        }
        getAs(lead, "/api/applications?size=2&page=0&sort=businessName&dir=asc")
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].businessName").value("Page Item 1"));
        getAs(lead, "/api/applications?size=2&page=2&sort=businessName&dir=asc")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].businessName").value("Page Item 5"));
    }

    @Test
    void timeInQueueIndicatorsFollowTheThresholds() throws Exception {
        appWaiting(Map.of("businessName", "Fresh"), 2);
        appWaiting(Map.of("businessName", "Getting old"), 30);
        appWaiting(Map.of("businessName", "Overdue"), 60);

        getAs(lead, "/api/applications?sort=timeInQueue&dir=asc")
                .andExpect(jsonPath("$.items[0].queueLevel").value("OK"))
                .andExpect(jsonPath("$.items[0].hoursInQueue").value(2))
                .andExpect(jsonPath("$.items[1].queueLevel").value("WARN"))
                .andExpect(jsonPath("$.items[2].queueLevel").value("CRITICAL"))
                .andExpect(jsonPath("$.items[2].hoursInQueue").value(60));
    }

    @Test
    void applicationsThatAreNotWaitingHaveNoQueueTime() throws Exception {
        String id = makeLive(lead, Map.of("businessName", "Already Live"));
        backdate(id, 500);

        getAs(lead, "/api/applications?status=LIVE")
                .andExpect(jsonPath("$.items[0].hoursInQueue", nullValue()))
                .andExpect(jsonPath("$.items[0].queueLevel").value("NONE"));
    }

    @Test
    void assignedToMeOnlyShowsMyApplications() throws Exception {
        String me = tokenFor(Role.REVIEWER);
        String mine = createApplication(me, Map.of("businessName", "Mine")).get("id").asText();
        createApplication(me, Map.of("businessName", "Nobody's yet"));
        String theirs = createApplication(lead, Map.of("businessName", "Theirs")).get("id").asText();
        transition(me, mine, "IN_REVIEW", null).andExpect(status().isOk());
        transition(lead, theirs, "IN_REVIEW", null).andExpect(status().isOk());

        getAs(me, "/api/applications?assignedToMe=true")
                .andExpect(jsonPath("$.items[*].businessName", containsInAnyOrder("Mine")));
        getAs(me, "/api/applications?assignedToMe=false").andExpect(jsonPath("$.totalElements").value(3));
    }
}
