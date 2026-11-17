package com.meridian.admin.web;

import com.meridian.admin.config.AuthUser;
import com.meridian.admin.domain.ApplicationStatus;
import com.meridian.admin.domain.CheckKey;
import com.meridian.admin.service.ApplicationService;
import com.meridian.admin.web.Dtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Every write method carries @PreAuthorize in addition to the URL rules in SecurityConfig.
 * The finer rule (for example, only an operations lead may suspend) lives in the service.
 */
@RestController
@RequestMapping("/api/applications")
@Tag(name = "Applications")
public class ApplicationController {

    private static final String CAN_WRITE = "hasAnyRole('REVIEWER','OPERATIONS_LEAD')";

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Review queue",
            description = "Filter by status and search text. sort is one of timeInQueue, submittedAt, "
                    + "businessName, status. For timeInQueue, dir=desc lists the longest wait first.")
    public PageDto<ApplicationSummaryDto> list(
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean assignedToMe,
            @RequestParam(defaultValue = "timeInQueue") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.list(status, q, assignedToMe, sort, dir, page, size, caller);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Application detail with pharmacy, documents, checklist and status history")
    public ApplicationDetailDto get(@PathVariable UUID id,
                                    @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.get(id, caller);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Submit a new pharmacy application (status Submitted)")
    public ApplicationDetailDto create(@Valid @RequestBody CreateApplicationRequest request,
                                       @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.create(request, caller);
    }

    @PostMapping("/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Record an uploaded document (metadata only) and re-run automatic checks")
    public ApplicationDetailDto addDocument(@PathVariable UUID id,
                                            @Valid @RequestBody DocumentRequest request,
                                            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.addDocument(id, request, caller);
    }

    @PostMapping("/{id}/checks/run")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Re-run the automatic checks against the current data")
    public ApplicationDetailDto runChecks(@PathVariable UUID id,
                                          @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.runChecks(id, caller);
    }

    @PutMapping("/{id}/checklist/{key}")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Record the result of a manual check (only while In Review)")
    public ApplicationDetailDto manualCheck(@PathVariable UUID id,
                                            @PathVariable CheckKey key,
                                            @Valid @RequestBody ManualCheckRequest request,
                                            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.recordManualCheck(id, key, request, caller);
    }

    @PostMapping("/{id}/transitions")
    @PreAuthorize(CAN_WRITE)
    @Operation(summary = "Move the application to a new status",
            description = "A reason is required for Needs More Info, Approved, Rejected and Suspended, "
                    + "and when reinstating a Suspended pharmacy. Approval is refused while any required check is not passing. "
                    + "Only an operations lead may go Live, suspend or reinstate.")
    public ApplicationDetailDto transition(@PathVariable UUID id,
                                           @Valid @RequestBody TransitionRequest request,
                                           @Parameter(hidden = true) @AuthenticationPrincipal AuthUser caller) {
        return service.transition(id, request, caller);
    }
}
