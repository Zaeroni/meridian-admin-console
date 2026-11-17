package com.meridian.admin.seed;

import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.domain.CheckKey;
import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.Document;
import com.meridian.admin.domain.DocumentType;
import com.meridian.admin.domain.OrderRecord;
import com.meridian.admin.domain.Pharmacy;
import com.meridian.admin.domain.Reviewer;
import com.meridian.admin.domain.Role;
import com.meridian.admin.domain.StatusHistory;
import com.meridian.admin.repo.ApplicationRepository;
import com.meridian.admin.repo.OrderRepository;
import com.meridian.admin.repo.PharmacyRepository;
import com.meridian.admin.repo.ReviewerRepository;
import com.meridian.admin.repo.StatusHistoryRepository;
import com.meridian.admin.service.ChecklistService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import static com.meridian.admin.domain.ApplicationStatus.*;

/**
 * Loads synthetic demo data when meridian.seed.enabled=true. Everything here is invented:
 * names, addresses, license numbers and orders. It uses a fixed random seed, so every run
 * produces the same pharmacies, and it does nothing if reviewers already exist.
 */
@Component
@ConditionalOnProperty(prefix = "meridian.seed", name = "enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    public static final String DEMO_PASSWORD = "Password123!";

    private static final String[] PREFIXES = {"Maple", "Cedar", "Riverside", "Lakeside", "Summit", "Harbor",
            "Oak Hill", "Willow", "Pinecrest", "Sunrise", "Bluebird", "Cornerstone", "Prairie", "Magnolia",
            "Redwood", "Heritage", "Evergreen", "Brookside", "Sterling", "Juniper", "Meadow", "Lone Star",
            "Bayou", "Ridgeview", "Crossroads", "Silverleaf", "Hilltop", "Parkside", "Northgate", "Fairview",
            "Copperfield", "Windmill", "Starlight", "Oakwood"};
    private static final String[] SUFFIXES = {"Pharmacy", "Drug", "Apothecary", "Family Pharmacy", "Health Pharmacy"};
    private static final String[] OWNERS = {"Dana Whitfield", "Priya Raman", "Marcus Bell", "Elena Torres",
            "Samir Haddad", "Grace Okafor", "Tomas Novak", "Hannah Lindgren", "Andre Beaumont", "Mei Tanaka",
            "Cole Jennings", "Naomi Adeyemi", "Victor Salazar", "Ingrid Moen", "Rashad Coleman", "Leah Goldberg"};
    private static final String[][] PLACES = {{"Dallas", "TX", "75201"}, {"Plano", "TX", "75024"},
            {"Fort Worth", "TX", "76102"}, {"Austin", "TX", "78701"}, {"Houston", "TX", "77002"},
            {"Tulsa", "OK", "74103"}, {"Little Rock", "AR", "72201"}, {"Baton Rouge", "LA", "70801"},
            {"Memphis", "TN", "38103"}, {"Oklahoma City", "OK", "73102"}};

    /** One pharmacy to create: where it ends up, how long it has been there, and any data problem. */
    private record Plan(ApplicationStatus finalStatus, long hoursInFinalStatus, Problem problem, int licenseDaysLeft) {
    }

    private enum Problem { NONE, MISSING_DOCUMENT, NO_RADIUS, BAD_LICENSE_FORMAT }

    private final ReviewerRepository reviewers;
    private final PharmacyRepository pharmacies;
    private final ApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final OrderRepository orders;
    private final ChecklistService checklist;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DataSeeder(ReviewerRepository reviewers, PharmacyRepository pharmacies, ApplicationRepository applications,
                      StatusHistoryRepository history, OrderRepository orders, ChecklistService checklist,
                      PasswordEncoder encoder, TransactionTemplate tx, Clock clock) {
        this.reviewers = reviewers;
        this.pharmacies = pharmacies;
        this.applications = applications;
        this.history = history;
        this.orders = orders;
        this.checklist = checklist;
        this.encoder = encoder;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (reviewers.count() > 0) {
            log.info("Seed skipped: reviewers already exist");
            return;
        }
        tx.executeWithoutResult(status -> seed());
        log.info("Seeded demo data. Log in as lead@meridian.test, reviewer@meridian.test or readonly@meridian.test "
                + "with password {}", DEMO_PASSWORD);
    }

    private void seed() {
        String hash = encoder.encode(DEMO_PASSWORD);
        Reviewer riley = reviewers.save(new Reviewer("reviewer@meridian.test", hash, "Riley Reviewer", Role.REVIEWER));
        Reviewer jordan = reviewers.save(new Reviewer("jordan@meridian.test", hash, "Jordan Reviewer", Role.REVIEWER));
        Reviewer morgan = reviewers.save(new Reviewer("lead@meridian.test", hash, "Morgan Lead", Role.OPERATIONS_LEAD));
        reviewers.save(new Reviewer("readonly@meridian.test", hash, "Casey ReadOnly", Role.READ_ONLY));
        List<Reviewer> staff = List.of(riley, jordan, morgan);

        Random random = new Random(42);
        Instant now = clock.instant();
        List<Pharmacy> liveOrSuspended = new ArrayList<>();

        List<Plan> plans = plans();
        for (int i = 0; i < plans.size(); i++) {
            Plan plan = plans.get(i);
            Pharmacy pharmacy = buildPharmacy(i, plan, random, now);
            pharmacies.save(pharmacy);
            buildApplication(pharmacy, plan, staff, random, now);
            if (plan.finalStatus() == LIVE || plan.finalStatus() == SUSPENDED) {
                liveOrSuspended.add(pharmacy);
            }
        }
        seedOrders(liveOrSuspended, random, now);
    }

    /** The mix of pharmacies. Fixed list so the dashboard always has something interesting to show. */
    private List<Plan> plans() {
        List<Plan> p = new ArrayList<>();
        // Submitted: one fresh, one 30h, one past the 48h limit, two with data problems.
        p.add(new Plan(SUBMITTED, 3, Problem.NONE, 700));
        p.add(new Plan(SUBMITTED, 30, Problem.MISSING_DOCUMENT, 700));
        p.add(new Plan(SUBMITTED, 60, Problem.NONE, 700));
        p.add(new Plan(SUBMITTED, 100, Problem.NO_RADIUS, 700));
        p.add(new Plan(SUBMITTED, 8, Problem.BAD_LICENSE_FORMAT, 700));
        // In review
        p.add(new Plan(IN_REVIEW, 5, Problem.NONE, 700));
        p.add(new Plan(IN_REVIEW, 26, Problem.NONE, 700));
        p.add(new Plan(IN_REVIEW, 55, Problem.MISSING_DOCUMENT, 700));
        p.add(new Plan(IN_REVIEW, 12, Problem.NONE, 700));
        // Needs more info: one recent, one in the warning band, one past 7 days.
        p.add(new Plan(NEEDS_MORE_INFO, 20, Problem.MISSING_DOCUMENT, 700));
        p.add(new Plan(NEEDS_MORE_INFO, 100, Problem.NO_RADIUS, 700));
        p.add(new Plan(NEEDS_MORE_INFO, 220, Problem.MISSING_DOCUMENT, 700));
        // Approved, waiting to go live
        p.add(new Plan(APPROVED, 18, Problem.NONE, 700));
        p.add(new Plan(APPROVED, 70, Problem.NONE, 700));
        // Rejected
        p.add(new Plan(REJECTED, 150, Problem.BAD_LICENSE_FORMAT, 700));
        p.add(new Plan(REJECTED, 400, Problem.MISSING_DOCUMENT, 700));
        // Live: most are healthy, two licenses expire soon, one has already expired.
        int[] liveLicenseDays = {640, 510, 420, 365, 300, 880, 740, 590, 450, 330, 25, 12, -5, 700};
        for (int i = 0; i < liveLicenseDays.length; i++) {
            p.add(new Plan(LIVE, 24L * (20 + i * 9), Problem.NONE, liveLicenseDays[i]));
        }
        // Suspended
        p.add(new Plan(SUSPENDED, 90, Problem.NONE, 500));
        p.add(new Plan(SUSPENDED, 300, Problem.NONE, 260));
        return p;
    }

    private Pharmacy buildPharmacy(int index, Plan plan, Random random, Instant now) {
        String name = PREFIXES[index % PREFIXES.length] + " " + SUFFIXES[index % SUFFIXES.length];
        String[] place = PLACES[index % PLACES.length];
        String owner = OWNERS[index % OWNERS.length];
        String state = place[1];

        Pharmacy p = new Pharmacy();
        p.setBusinessName(name);
        p.setOwnerName(owner);
        p.setOwnerEmail("owner" + (index + 1) + "@example.test");
        p.setPhone("555-01" + String.format("%02d", index % 100));
        p.setAddressLine((100 + index * 7) + " Main Street");
        p.setCity(place[0]);
        p.setState(state);
        p.setPostalCode(place[2]);
        p.setLicenseState(state);
        p.setLicenseNumber(plan.problem() == Problem.BAD_LICENSE_FORMAT
                ? state + (1000 + index)
                : state + "-" + (1000000 + random.nextInt(8000000)));
        p.setLicenseHolderName(name);
        p.setLicenseExpiration(LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(plan.licenseDaysLeft()));
        p.setDeliveryRadiusMiles(plan.problem() == Problem.NO_RADIUS ? null : BigDecimal.valueOf(5 + random.nextInt(21)));
        p.setStorefrontName(name);
        p.setStorefrontSlug(name.toLowerCase().replace(' ', '-') + "-" + (index + 1));
        return p;
    }

    private void buildApplication(Pharmacy pharmacy, Plan plan, List<Reviewer> staff, Random random, Instant now) {
        List<ApplicationStatus> path = pathTo(plan.finalStatus());

        // Work out when each step happened, walking back from "now" through the final step.
        Instant[] at = new Instant[path.size()];
        at[path.size() - 1] = now.minus(Duration.ofHours(plan.hoursInFinalStatus()));
        for (int i = path.size() - 2; i >= 0; i--) {
            at[i] = at[i + 1].minus(Duration.ofHours(6 + random.nextInt(40)));
        }

        Reviewer reviewer = staff.get(random.nextInt(2)); // the two reviewers handle review work
        Reviewer lead = staff.get(2);

        var app = new Application(pharmacy, at[0]);
        addDocuments(app, plan.problem());
        checklist.initialize(app, at[0]);
        applications.save(app); // gives the application its id, which the history rows need

        history.save(new StatusHistory(app.getId(), null, SUBMITTED, lead, "Application submitted", at[0]));
        for (int i = 1; i < path.size(); i++) {
            ApplicationStatus to = path.get(i);
            boolean leadOnly = to == LIVE || to == SUSPENDED;
            Reviewer actor = leadOnly ? lead : reviewer;
            if (to == IN_REVIEW && path.get(i - 1) == SUBMITTED) {
                app.assignTo(reviewer);
            }
            app.moveTo(to, at[i]);
            history.save(new StatusHistory(app.getId(), path.get(i - 1), to, actor, reasonFor(to), at[i]));
        }

        applyManualChecks(app, plan.finalStatus(), reviewer, now);
        // Open applications show today's automatic results; decided ones keep what was true at decision time.
        if (plan.finalStatus().isOpen()) {
            checklist.refreshAutomatic(app, now);
        }
    }

    private static List<ApplicationStatus> pathTo(ApplicationStatus finalStatus) {
        return switch (finalStatus) {
            case SUBMITTED -> List.of(SUBMITTED);
            case IN_REVIEW -> List.of(SUBMITTED, IN_REVIEW);
            case NEEDS_MORE_INFO -> List.of(SUBMITTED, IN_REVIEW, NEEDS_MORE_INFO);
            case APPROVED -> List.of(SUBMITTED, IN_REVIEW, APPROVED);
            case REJECTED -> List.of(SUBMITTED, IN_REVIEW, REJECTED);
            case LIVE -> List.of(SUBMITTED, IN_REVIEW, APPROVED, LIVE);
            case SUSPENDED -> List.of(SUBMITTED, IN_REVIEW, APPROVED, LIVE, SUSPENDED);
        };
    }

    private static String reasonFor(ApplicationStatus to) {
        return switch (to) {
            case NEEDS_MORE_INFO -> "Please upload the missing documents and confirm the delivery radius.";
            case APPROVED -> "All checks passed. License and documents verified.";
            case REJECTED -> "License details could not be verified.";
            case SUSPENDED -> "Customer complaint under investigation.";
            default -> null;
        };
    }

    private void addDocuments(Application app, Problem problem) {
        DocumentType skipped = problem == Problem.MISSING_DOCUMENT ? DocumentType.OWNER_ID : null;
        for (DocumentType type : DocumentType.values()) {
            if (type == skipped) {
                continue;
            }
            var doc = new Document(app, type, type.name().toLowerCase() + ".pdf", "application/pdf", 120_000);
            app.getDocuments().add(doc);
        }
    }

    private void applyManualChecks(Application app, ApplicationStatus status, Reviewer reviewer, Instant now) {
        CheckResult result = switch (status) {
            case APPROVED, LIVE, SUSPENDED -> CheckResult.PASS;
            case REJECTED -> CheckResult.FAIL;
            default -> CheckResult.PENDING;
        };
        if (status == IN_REVIEW && app.getPharmacy().getBusinessName().length() % 2 == 0) {
            result = CheckResult.PASS; // some in-review applications are partly done
        }
        if (result == CheckResult.PENDING) {
            return;
        }
        String note = result == CheckResult.PASS ? "Name on license matches." : "License holder name does not match.";
        for (var item : app.getChecklist()) {
            if (item.getCheckKey() == CheckKey.BUSINESS_NAME_MATCH) {
                item.record(result, note, reviewer, now.minus(Duration.ofHours(2)));
            }
        }
    }

    private void seedOrders(List<Pharmacy> livePharmacies, Random random, Instant now) {
        List<OrderRecord> rows = new ArrayList<>();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        for (Pharmacy p : livePharmacies) {
            for (int d = 0; d < 30; d++) {
                int count = random.nextInt(6);
                for (int n = 0; n < count; n++) {
                    Instant placed = today.minusDays(d).atStartOfDay().toInstant(ZoneOffset.UTC)
                            .plusSeconds(8 * 3600 + random.nextInt(12 * 3600));
                    if (placed.isAfter(now)) {
                        continue;
                    }
                    rows.add(new OrderRecord(p.getId(), 1500 + random.nextInt(8000), placed));
                }
            }
        }
        orders.saveAll(rows);
    }
}
