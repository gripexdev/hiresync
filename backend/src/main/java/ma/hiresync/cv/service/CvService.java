package ma.hiresync.cv.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.auth.UserRepository;
import ma.hiresync.cv.dto.*;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.messaging.OptimizationMessage;
import ma.hiresync.cv.messaging.OptimizationProducer;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CvService {

    private final CvRepository             cvRepo;
    private final CvOptimizationRepository optimRepo;
    private final UserRepository           userRepo;
    private final AtsScorer                atsScorer;
    private final OptimizationProducer     producer;

    @Value("${hiresync.cv.upload-dir}")
    private String uploadDir;

    // ── GET /api/cv/versions ─────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CvResponse> getAllCvs(UUID userId) {
        return cvRepo.findByUserIdOrderByUploadedAtDesc(userId)
                .stream()
                .map(cv -> {
                    Map<String, String> sections = cv.getExtractedText() != null
                        ? atsScorer.parseSections(cv.getExtractedText())
                        : Map.of();
                    return CvResponse.from(cv, sections);
                })
                .toList();
    }

    // ── POST /api/cv/upload ───────────────────────────────────────────────────
    public CvResponse upload(MultipartFile file, UUID userId) throws IOException {
        validateFile(file);

        // 1. Extract text from PDF
        String text = atsScorer.extractText(file);

        // 2. Compute ATS score
        int score = atsScorer.computeScore(text);

        // 3. Save file to disk
        Path dir = Paths.get(uploadDir, userId.toString());
        Files.createDirectories(dir);
        String savedName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path dest = dir.resolve(savedName);
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        // 4. Persist CV entity
        var user = userRepo.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        var cv = Cv.builder()
                .user(user)
                .fileName(file.getOriginalFilename())
                .filePath(dest.toString())
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .atsScore(score)
                .extractedText(text.length() > 10_000 ? text.substring(0, 10_000) : text)
                .active(cvRepo.findByUserIdOrderByUploadedAtDesc(userId).isEmpty()) // first CV is auto-active
                .build();

        cvRepo.save(cv);
        log.info("CV uploaded for user {}: {} | ATS score: {}", userId, file.getOriginalFilename(), score);

        return CvResponse.from(cv, atsScorer.parseSections(text));
    }

    // ── PATCH /api/cv/{id}/activate ───────────────────────────────────────────
    public void activate(UUID cvId, UUID userId) {
        var cv = cvRepo.findByIdAndUserId(cvId, userId)
            .orElseThrow(() -> new RuntimeException("CV not found"));
        cvRepo.deactivateAll(userId);
        cv.setActive(true);
        cvRepo.save(cv);
    }

    // ── DELETE /api/cv/{id} ───────────────────────────────────────────────────
    public void delete(UUID cvId, UUID userId) {
        var cv = cvRepo.findByIdAndUserId(cvId, userId)
            .orElseThrow(() -> new RuntimeException("CV not found"));
        // Delete file from disk
        try { Files.deleteIfExists(Paths.get(cv.getFilePath())); }
        catch (IOException e) { log.warn("Could not delete file: {}", cv.getFilePath()); }
        cvRepo.delete(cv);
    }

    // ── POST /api/cv/optimize ─────────────────────────────────────────────────
    public OptimizeTriggerResponse triggerOptimization(OptimizeRequest req, UUID userId) {
        var cv = cvRepo.findByIdAndUserId(req.cvId(), userId)
            .orElseThrow(() -> new RuntimeException("CV not found"));

        // Create optimization record (status = QUEUED)
        var optim = CvOptimization.builder()
                .cv(cv)
                .jobId(req.jobId())
                .jobTitle(req.jobTitle())
                .company(req.company())
                .originalScore(cv.getAtsScore())
                .build();
        optimRepo.save(optim);
        optimRepo.flush();  // flush to DB immediately

        // ⚠️ Publish AFTER transaction commits — prevents consumer from running
        //    before the DB row is visible (race condition via TransactionSynchronization)
        final var message = new OptimizationMessage(
            optim.getId(), cv.getId(), userId, req.jobDescription());
        org.springframework.transaction.support.TransactionSynchronizationManager
            .registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        producer.send(message);
                    }
                });

        return new OptimizeTriggerResponse(
            optim.getId(), "queued",
            "Votre CV a été mis en file d'attente pour optimisation par Gemma 4 31B."
        );
    }

    // ── GET /api/cv/optimize/{id} ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public OptimizationResponse getOptimization(UUID optimId, UUID userId) {
        var optim = optimRepo.findByIdAndCvUserId(optimId, userId)
            .orElseThrow(() -> new RuntimeException("Optimization not found"));
        Object changes = parseChanges(optim.getSuggestedChangesJson());
        return OptimizationResponse.from(optim, changes);
    }

    // ── GET /api/cv/optimization-history ─────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OptimizationResponse> getHistory(UUID userId) {
        return optimRepo.findByCvUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(o -> OptimizationResponse.from(o, parseChanges(o.getSuggestedChangesJson())))
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        String ct = file.getContentType();
        if (ct == null || (!ct.equals("application/pdf")
                        && !ct.equals("application/msword")
                        && !ct.contains("officedocument"))) {
            throw new IllegalArgumentException("Only PDF and Word files are accepted");
        }
        if (file.getSize() > 10 * 1024 * 1024) throw new IllegalArgumentException("File exceeds 10 MB");
    }

    /**
     * Parse LLM response into a list of change objects.
     *
     * LLMs often wrap JSON in markdown code fences:
     *   ```json\n[...]\n```
     * This method strips those before parsing.
     */
    private Object parseChanges(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        // Strip markdown code fences: ```json ... ``` or ``` ... ```
        String json = raw.trim()
            .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
            .replaceAll("(?s)\\s*```$", "")
            .trim();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            // If the model returned an object with a key containing the array, unwrap it
            if (node.isObject()) {
                var iter = node.fields();
                while (iter.hasNext()) {
                    var entry = iter.next();
                    if (entry.getValue().isArray()) {
                        return mapper.convertValue(entry.getValue(), Object.class);
                    }
                }
            }
            return mapper.convertValue(node, Object.class);
        } catch (Exception e) {
            log.warn("Could not parse LLM JSON response: {}", e.getMessage());
            return List.of();
        }
    }
}
