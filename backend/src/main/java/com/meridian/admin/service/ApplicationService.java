package com.meridian.admin.service;

import com.meridian.admin.config.AuthUser;
import com.meridian.admin.domain.Application;
import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.domain.CheckKey;
import com.meridian.admin.domain.CheckResult;
import com.meridian.admin.domain.Document;
import com.meridian.admin.domain.Pharmacy;
import com.meridian.admin.domain.Reviewer;
import com.meridian.admin.domain.StatusHistory;
import com.meridian.admin.repo.ApplicationRepository;
import com.meridian.admin.repo.PharmacyRepository;
import com.meridian.admin.repo.ReviewerRepository;
import com.meridian.admin.repo.StatusHistoryRepository;
import com.meridian.admin.web.Dtos.*;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final List<String> SORT_KEYS = List.of("timeInQueue", "submittedAt", "businessName", "status");

    private final ApplicationRepository applications;
    private final PharmacyRepository pharmacies;
    private final ReviewerRepository reviewers;
    private final StatusHistoryRepository history;
    private final ChecklistService checklist;
    private final ApplicationMapper mapper;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, PharmacyRepository pharmacies,
                              ReviewerRepository reviewers, StatusHistoryRepository history,
                              ChecklistService checklist, ApplicationMapper mapper, Clock clock) {
        this.applications = applications;
        this.pharmacies = pharmacies;
        this.reviewers = reviewers;
        this.history = history;
        this.checklist = checklist;
        this.mapper = mapper;
        this.clock = clock;
    }

    // ---------- Reads ----------

    @Transactional(readOnly = true)
    public PageDto<ApplicationSummaryDto> list(Collection<ApplicationStatus> statuses, String search,
                                               boolean assignedToMe, String sortKey, String dir,
                                               int page, int size, AuthUser caller) {
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        if (page < 0) {
            throw new BadRequestException("page must be 0 or higher");
        }
        var pageable = PageRequest.of(page, size, sortFor(sortKey, dir));
        var spec = ApplicationSpecs.queue(statuses, search, assignedToMe ? caller.id() : null);
        var result = applications.findAll(spec, pageable);
        return new PageDto<>(result.getContent().stream().map(mapper::summary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ApplicationDetailDto get(UUID id, AuthUser caller) {
        return detail(find(id), caller);
    }

    // ---------- Writes ----------

    @Transactional
    public ApplicationDetailDto create(CreateApplicationRequest req, AuthUser actor) {
        if (pharmacies.existsByStorefrontSlug(req.storefrontSlug())) {
            throw new ConflictException("Storefront slug '" + req.storefrontSlug() + "' is already in use");
        }
        var now = clock.instant();

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setBusinessName(req.businessName().trim());
        pharmacy.setOwnerName(req.ownerName().trim());
        pharmacy.setOwnerEmail(req.ownerEmail().trim());
        pharmacy.setPhone(req.phone());
        pharmacy.setAddressLine(req.addressLine().trim());
        pharmacy.setCity(req.city().trim());
        pharmacy.setState(req.state());
        pharmacy.setPostalCode(req.postalCode());
        pharmacy.setLicenseNumber(req.licenseNumber().trim());
        pharmacy.setLicenseState(req.licenseState());
        pharmacy.setLicenseHolderName(req.licenseHolderName().trim());
        pharmacy.setLicenseExpiration(req.licenseExpiration());
        pharmacy.setDeliveryRadiusMiles(req.deliveryRadiusMiles());
        pharmacy.setStorefrontName(req.storefrontName().trim());
        pharmacy.setStorefrontSlug(req.storefrontSlug());
        pharmacies.save(pharmacy);

        var application = new Application(pharmacy, now);
        checklist.initialize(application, now);
        applications.save(application);

        history.save(new StatusHistory(application.getId(), null, ApplicationStatus.SUBMITTED,
                actorRef(actor), "Application submitted", now));
        return detail(application, actor);
    }

    @Transactional
    public ApplicationDetailDto addDocument(UUID id, DocumentRequest req, AuthUser actor) {
        Application application = find(id);
        requireOpen(application, "Documents can only be added while the application is open");
        application.getDocuments().add(new Document(application, req.docType(), req.fileName().trim(),
                req.contentType(), req.sizeBytes()));
        checklist.refreshAutomatic(application, clock.instant());
        return detail(application, actor);
    }

    @Transactional
    public ApplicationDetailDto runChecks(UUID id, AuthUser actor) {
        Application application = find(id);
        requireOpen(application, "Checks are frozen once a decision has been made");
        checklist.refreshAutomatic(application, clock.instant());
        return detail(application, actor);
    }

    @Transactional
    public ApplicationDetailDto recordManualCheck(UUID id, CheckKey key, ManualCheckRequest req, AuthUser actor) {
        Application application = find(id);
        if (key.automatic()) {
            throw new BadRequestException(key + " is an automatic check and cannot be set by hand");
        }
        if (req.result() == CheckResult.PENDING) {
            throw new BadRequestException("A manual check must be recorded as PASS or FAIL");
        }
        if (application.getStatus() != ApplicationStatus.IN_REVIEW) {
            throw new ConflictException("Manual checks can only be recorded while the application is In Review");
        }
        var item = application.getChecklist().stream()
                .filter(c -> c.getCheckKey() == key).findFirst()
                .orElseThrow(() -> new NotFoundException("Checklist item " + key + " not found"));
        item.record(req.result(), blankToNull(req.note()), actorRef(actor), clock.instant());
        return detail(application, actor);
    }

    /**
     * Moves an application to a new status. Order of checks: is the move legal, is this role allowed
     * to make it, is a reason supplied when one is required, do the checks pass when approving.
     * The row is locked so two people cannot apply conflicting moves at the same time.
     * noRollbackFor keeps the freshly re-evaluated checks saved even when approval is refused,
     * so the screen shows the real reason on the next load.
     */
    @Transactional(noRollbackFor = ChecksNotPassingException.class)
    public ApplicationDetailDto transition(UUID id, TransitionRequest req, AuthUser actor) {
        Application application = applications.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
        ApplicationStatus from = application.getStatus();
        ApplicationStatus to = req.to();

        var rule = TransitionRules.find(from, to).orElseThrow(() -> new ConflictException(
                "Cannot move from " + from + " to " + to + ". Allowed from " + from + ": "
                        + TransitionRules.nextStatuses(from)));

        if (!rule.allowedRoles().contains(actor.role())) {
            throw new AccessDeniedException("Role " + actor.role() + " cannot move an application to " + to);
        }

        String reason = blankToNull(req.reason());
        if (rule.reasonRequired() && reason == null) {
            throw new BadRequestException("A reason is required to move to " + to);
        }

        var now = clock.instant();
        if (rule.requiresPassingChecks()) {
            // Re-run first so a license that expired during review cannot slip through on stale results.
            checklist.refreshAutomatic(application, now);
            var blockers = checklist.blockers(application);
            if (!blockers.isEmpty()) {
                throw new ChecksNotPassingException(
                        blockers.stream().map(ChecklistService::describe).collect(Collectors.toList()));
            }
        }

        Reviewer actorRef = actorRef(actor);
        if (from == ApplicationStatus.SUBMITTED && to == ApplicationStatus.IN_REVIEW) {
            application.assignTo(actorRef);
            checklist.refreshAutomatic(application, now);
        }
        application.moveTo(to, now);
        history.save(new StatusHistory(application.getId(), from, to, actorRef, reason, now));
        return detail(application, actor);
    }

    // ---------- Helpers ----------

    private Application find(UUID id) {
        return applications.findById(id)
                .orElseThrow(() -> new NotFoundException("Application " + id + " not found"));
    }

    private ApplicationDetailDto detail(Application application, AuthUser caller) {
        var rows = history.findByApplicationIdOrderByCreatedAtAscIdAsc(application.getId());
        return mapper.detail(application, rows, caller.role());
    }

    private Reviewer actorRef(AuthUser actor) {
        return reviewers.getReferenceById(actor.id());
    }

    private static void requireOpen(Application application, String message) {
        if (!application.getStatus().isOpen()) {
            throw new ConflictException(message + " (status is " + application.getStatus() + ")");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Only whitelisted sort keys are accepted. "timeInQueue" descending means longest wait first,
     * which is the oldest status change, so the stored column is sorted the other way round.
     */
    private static Sort sortFor(String key, String dir) {
        boolean desc = !"asc".equalsIgnoreCase(dir);
        if (!"asc".equalsIgnoreCase(dir) && !"desc".equalsIgnoreCase(dir)) {
            throw new BadRequestException("dir must be asc or desc");
        }
        Sort.Direction direction = desc ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort primary = switch (key) {
            case "timeInQueue" -> Sort.by(desc ? Sort.Direction.ASC : Sort.Direction.DESC, "statusChangedAt");
            case "submittedAt" -> Sort.by(direction, "submittedAt");
            case "businessName" -> Sort.by(direction, "pharmacy.businessName");
            case "status" -> Sort.by(direction, "status");
            default -> throw new BadRequestException("Unknown sort '" + key + "'. Allowed: " + SORT_KEYS);
        };
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
