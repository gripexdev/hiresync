package ma.hiresync.application.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.hiresync.application.dto.ApplicationResponse;
import ma.hiresync.application.dto.ApplicationStatsResponse;
import ma.hiresync.application.dto.ApplyRequest;
import ma.hiresync.application.dto.UpdateStatusRequest;
import ma.hiresync.application.entity.JobApplication.ApplicationStatus;
import ma.hiresync.application.service.ApplicationService;
import ma.hiresync.auth.service.JwtService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final JwtService         jwtService;

    /** POST /api/applications/{jobId} — apply to a job with a chosen CV. */
    @PostMapping("/{jobId}")
    public ResponseEntity<ApplicationResponse> apply(
            @PathVariable UUID jobId,
            @Valid @RequestBody ApplyRequest req,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.apply(userId, jobId, req));
    }

    /**
     * POST /api/applications/{jobId}/mark-applied — record that the user clicked
     * "Postuler" to apply on the company's site. Idempotent.
     */
    @PostMapping("/{jobId}/mark-applied")
    public ResponseEntity<ApplicationResponse> markApplied(
            @PathVariable UUID jobId,
            @Valid @RequestBody ApplyRequest req,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(applicationService.markApplied(userId, jobId, req.cvId()));
    }

    /** PATCH /api/applications/{id}/status — change an application's status. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest req,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(applicationService.updateStatus(userId, id, req.status()));
    }

    /**
     * GET /api/applications — the current user's applications, server-side paginated.
     * Optional {@code status} filter powers per-column kanban lazy-loading; without it
     * the full list is paginated for the table view. Newest first.
     */
    @GetMapping
    public ResponseEntity<Page<ApplicationResponse>> myApplications(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        var pageable = PageRequest.of(page, size, Sort.by("appliedAt").descending());
        return ResponseEntity.ok(
                applicationService.getMyApplications(extractUserId(authHeader), parseStatus(status), pageable));
    }

    /** GET /api/applications/stats — kanban/dashboard counters. */
    @GetMapping("/stats")
    public ResponseEntity<ApplicationStatsResponse> stats(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(applicationService.getStats(extractUserId(authHeader)));
    }

    /** GET /api/applications/check/{jobId} — has the user already applied? */
    @GetMapping("/check/{jobId}")
    public ResponseEntity<Map<String, Boolean>> check(
            @PathVariable UUID jobId,
            @RequestHeader("Authorization") String authHeader) {
        boolean applied = applicationService.hasApplied(extractUserId(authHeader), jobId);
        return ResponseEntity.ok(Map.of("applied", applied));
    }

    /** Parse an optional status filter; null / blank / "all" means "no filter". */
    private static ApplicationStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("all")) return null;
        try {
            return ApplicationStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut invalide : " + raw);
        }
    }

    private UUID extractUserId(String authHeader) {
        return jwtService.extractUserId(authHeader.replace("Bearer ", ""));
    }
}
