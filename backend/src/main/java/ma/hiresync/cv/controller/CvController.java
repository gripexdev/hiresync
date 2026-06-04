package ma.hiresync.cv.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.hiresync.auth.JwtService;
import ma.hiresync.cv.dto.*;
import ma.hiresync.cv.service.CvService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
public class CvController {

    private final CvService  cvService;
    private final JwtService jwtService;

    /** GET /api/cv/versions — list all CVs for the authenticated user */
    @GetMapping("/versions")
    public ResponseEntity<List<CvResponse>> getVersions(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.getAllCvs(userId));
    }

    /**
     * POST /api/cv/upload — upload a PDF/Word CV.
     * Triggers PDFBox extraction + ATS scoring synchronously.
     * Response includes the parsed sections and initial ATS score.
     */
    @PostMapping("/upload")
    public ResponseEntity<CvResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authHeader) throws IOException {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.upload(file, userId));
    }

    /** PATCH /api/cv/{id}/activate — set this CV as the active one */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        cvService.activate(id, extractUserId(authHeader));
        return ResponseEntity.noContent().build();
    }

    /** DELETE /api/cv/{id} */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        cvService.delete(id, extractUserId(authHeader));
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/cv/optimize — trigger async optimization via RabbitMQ.
     * Returns immediately with {optimizationId, status:"queued"}.
     * Result comes back via WebSocket /user/topic/cv-optimization.
     */
    @PostMapping("/optimize")
    public ResponseEntity<OptimizeTriggerResponse> optimize(
            @Valid @RequestBody OptimizeRequest req,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.accepted().body(cvService.triggerOptimization(req, userId));
    }

    /** GET /api/cv/optimize/{id} — poll optimization result (WebSocket fallback) */
    @GetMapping("/optimize/{id}")
    public ResponseEntity<OptimizationResponse> getOptimization(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.getOptimization(id, userId));
    }

    /** GET /api/cv/optimization-history */
    @GetMapping("/optimization-history")
    public ResponseEntity<List<OptimizationResponse>> getHistory(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.getHistory(userId));
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractUserId(token);
    }
}
