package ma.hiresync.cv.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import ma.hiresync.cv.service.AtsScorer;
import ma.hiresync.cv.service.OpenRouterService;
import ma.hiresync.notification.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Consumes CV optimization jobs from RabbitMQ cv.optimize.queue.
 *
 * @Transactional keeps the JPA session open for the full duration
 * of the consumer, preventing LazyInitializationException on cv.user
 * and cv.extractedText.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OptimizationConsumer {

    private final CvOptimizationRepository optimRepo;
    private final CvRepository             cvRepo;
    private final OpenRouterService        openRouter;
    private final AtsScorer                atsScorer;
    private final NotificationService      notificationSvc;
    private final ObjectMapper             objectMapper;

    @RabbitListener(queues = RabbitMQConfig.CV_OPTIMIZE_QUEUE)
    @Transactional
    public void consume(OptimizationMessage msg) {
        log.info("Received optimization job: {}", msg.optimizationId());
        long start = System.currentTimeMillis();

        // Load the optimization record — still QUEUED at this point
        var optim = optimRepo.findById(msg.optimizationId()).orElse(null);
        if (optim == null) {
            log.error("Optimization not found in DB: {}", msg.optimizationId());
            return;
        }

        // Load Cv and User explicitly with fresh queries (avoids lazy proxy issues)
        Cv cv = cvRepo.findById(msg.cvId()).orElse(null);
        if (cv == null) {
            log.error("CV not found: {}", msg.cvId());
            return;
        }

        // Access user ID directly from the message (passed at queue time — no lazy load needed)
        UUID userId = msg.userId();

        try {
            // ── Step 1: Mark as PROCESSING ───────────────────────────────────
            optim.setStatus(CvOptimization.OptimizationStatus.PROCESSING);
            optimRepo.save(optim);
            notificationSvc.pushCvOptimizationEvent(userId, optim.getId(), "processing",
                "Analyse et réécriture en cours par Gemma 4 31B…");

            // ── Step 2: Call OpenRouter (Gemma 4 31B free) ───────────────────
            String cvText = cv.getExtractedText() != null ? cv.getExtractedText() : "";
            String llmResponse = openRouter.optimizeCv(cvText, msg.jobDescription());

            // ── Step 3: Split the combined response into {suggestions, optimizedCv}
            String suggestionsJson = extractSuggestions(llmResponse);
            String optimizedCvJson = extractOptimizedCv(llmResponse);

            // ── Step 4: Compute new ATS score ────────────────────────────────
            int suggCount  = countSuggestions(suggestionsJson);
            int newScore   = Math.min(cv.getAtsScore() + (suggCount * 5) + 8, 100);

            // ── Step 5: Persist result ───────────────────────────────────────
            optim.setStatus(CvOptimization.OptimizationStatus.COMPLETED);
            optim.setOptimizedScore(newScore);
            optim.setSuggestedChangesJson(suggestionsJson);
            optim.setOptimizedCvJson(optimizedCvJson);
            optim.setModelUsed("google/gemma-4-31b-it:free");
            optim.setProcessingTimeMs(System.currentTimeMillis() - start);
            optim.setCompletedAt(Instant.now());
            optimRepo.save(optim);

            // ── Step 5: Push WebSocket notification ──────────────────────────
            notificationSvc.pushCvOptimizationEvent(
                userId, optim.getId(), "completed",
                String.format("CV optimisé ! Score : %d%% → %d%%", cv.getAtsScore(), newScore)
            );
            log.info("Optimization {} completed in {}ms. Score {}%→{}%",
                optim.getId(), System.currentTimeMillis() - start, cv.getAtsScore(), newScore);

        } catch (Exception e) {
            log.error("Optimization {} failed: {}", msg.optimizationId(), e.getMessage(), e);
            optim.setStatus(CvOptimization.OptimizationStatus.FAILED);
            optim.setProcessingTimeMs(System.currentTimeMillis() - start);
            optimRepo.save(optim);
            notificationSvc.pushCvOptimizationEvent(
                userId, optim.getId(), "failed",
                "Optimisation échouée (quota OpenRouter ?). Réessayez dans 1 minute."
            );
        }
    }

    private int countSuggestions(String json) {
        try {
            var node = objectMapper.readTree(json);
            return node.isArray() ? node.size() : 3;
        } catch (Exception e) {
            return 3;
        }
    }

    /** Strip markdown fences and return the parsed root node, or null. */
    private com.fasterxml.jackson.databind.JsonNode parseRoot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String json = raw.trim()
            .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
            .replaceAll("(?s)\\s*```$", "").trim();
        try { return objectMapper.readTree(json); }
        catch (Exception e) { return null; }
    }

    /** Extract the "suggestions" array as JSON (handles object-wrapped or bare-array responses). */
    private String extractSuggestions(String llmResponse) {
        var root = parseRoot(llmResponse);
        try {
            if (root == null) return "[]";
            if (root.isArray()) return root.toString();              // bare array (old format)
            if (root.has("suggestions")) return root.get("suggestions").toString();
            return "[]";
        } catch (Exception e) {
            return "[]";
        }
    }

    /** Extract the "optimizedCv" object as JSON, or null if not present. */
    private String extractOptimizedCv(String llmResponse) {
        var root = parseRoot(llmResponse);
        try {
            if (root != null && root.has("optimizedCv")) {
                return root.get("optimizedCv").toString();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
