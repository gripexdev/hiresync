package ma.hiresync.application.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.hiresync.application.dto.ApplicationResponse;
import ma.hiresync.application.dto.ApplicationStatsResponse;
import ma.hiresync.application.dto.ApplyRequest;
import ma.hiresync.application.service.ApplicationService;
import ma.hiresync.auth.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /** GET /api/applications — the current user's applications. */
    @GetMapping
    public ResponseEntity<List<ApplicationResponse>> myApplications(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(applicationService.getMyApplications(extractUserId(authHeader)));
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

    private UUID extractUserId(String authHeader) {
        return jwtService.extractUserId(authHeader.replace("Bearer ", ""));
    }
}
