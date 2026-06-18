package ma.hiresync.cv.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.hiresync.auth.service.JwtService;
import ma.hiresync.cv.dto.*;
import ma.hiresync.cv.service.CvService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
public class CvController {

    private final CvService        cvService;
    private final JwtService       jwtService;
    private final ma.hiresync.cv.service.PdfRenderService pdfRenderService;

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

    /**
     * PATCH /api/cv/optimize/{id}/boost-keywords
     * Inject user-selected missing keywords into the optimized CV skills,
     * re-run ATS scoring, and return the updated result.
     */
    @PatchMapping("/optimize/{id}/boost-keywords")
    public ResponseEntity<OptimizationResponse> boostKeywords(
            @PathVariable UUID id,
            @RequestBody BoostKeywordsRequest req,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.boostKeywords(id, userId, req.keywords()));
    }

    /**
     * POST /api/cv/optimize/{id}/cover-letter?regenerate=false
     * Generate (or return the cached) cover letter / application email for the
     * optimization, grounded in the optimized CV + the original job posting.
     */
    @PostMapping("/optimize/{id}/cover-letter")
    public ResponseEntity<CoverLetterResponse> coverLetter(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean regenerate,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.generateCoverLetter(id, userId, regenerate));
    }

    /** GET /api/cv/optimization-history */
    @GetMapping("/optimization-history")
    public ResponseEntity<List<OptimizationResponse>> getHistory(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.getHistory(userId));
    }

    /**
     * GET /api/cv/structured/{optimizationId}
     * Returns the structured optimized CV (name, summary, experience, skills…)
     * for the CV Studio to render into templates.
     */
    @GetMapping("/structured/{optimizationId}")
    public ResponseEntity<Object> getStructuredCv(
            @PathVariable UUID optimizationId,
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return ResponseEntity.ok(cvService.getStructuredCv(optimizationId, userId));
    }

    /**
     * GET /api/cv/download/{optimizationId}  (legacy PDFBox path — kept as fallback)
     * Generates a basic PDF from the optimized CV text.
     */
    @GetMapping("/download/{optimizationId}")
    public ResponseEntity<byte[]> downloadOptimizedCv(
            @PathVariable UUID optimizationId,
            @RequestHeader("Authorization") String authHeader) throws IOException {
        UUID userId = extractUserId(authHeader);
        byte[] pdf  = cvService.generateOptimizedCvPdf(optimizationId, userId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"CV_HireSync_Optimised.pdf\"")
                .body(pdf);
    }

    /**
     * POST /api/cv/render-pdf
     * Takes the fully-designed CV HTML (from the Angular CV Studio) and renders it
     * to a pixel-perfect VECTOR PDF using headless Chromium (Playwright).
     * This is the high-quality path: selectable text, crisp, ATS-readable.
     */
    @PostMapping("/render-pdf")
    public ResponseEntity<byte[]> renderPdf(
            @Valid @RequestBody ma.hiresync.cv.dto.RenderPdfRequest req,
            @RequestHeader("Authorization") String authHeader) {
        extractUserId(authHeader);   // validates JWT
        byte[] pdf = pdfRenderService.htmlToPdf(req.html());

        String name = (req.fileName() != null && !req.fileName().isBlank())
            ? req.fileName() : "CV_HireSync.pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "\"")
                .body(pdf);
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractUserId(token);
    }
}
