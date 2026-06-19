package ma.hiresync.cv.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import ma.hiresync.cv.service.AiGatewayService;
import ma.hiresync.cv.service.AtsScorer;
import ma.hiresync.notification.NotificationService;
import ma.hiresync.notification.entity.NotificationType;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    /** Below this CV↔job fit score, optimization is refused even if the LLM flags "compatible". */
    private static final int MIN_COMPATIBILITY_SCORE = 45;

    private final CvOptimizationRepository optimRepo;
    private final CvRepository             cvRepo;
    private final AiGatewayService         aiGateway;
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
                "Vérification de la compatibilité entre votre profil et l'offre…");

            String cvText = cv.getExtractedText() != null ? cv.getExtractedText() : "";

            // ── Step 1.5: Pre-flight compatibility gate ──────────────────────
            // Stop unrealistic optimizations (e.g. software dev → commercial assistant)
            // instead of fabricating a CV that doesn't fit the profession.
            var verdict = aiGateway.assessCompatibility(cvText, optim.getJobTitle(), msg.jobDescription());
            optim.setCompatibilityScore(verdict.score());
            optim.setCandidateProfile(verdict.candidateProfile());
            optim.setTargetProfile(verdict.targetProfile());

            if (!verdict.compatible() || verdict.score() < MIN_COMPATIBILITY_SCORE) {
                optim.setStatus(CvOptimization.OptimizationStatus.REJECTED);
                optim.setRejectionReason(verdict.verdict());
                optim.setProcessingTimeMs(System.currentTimeMillis() - start);
                optim.setCompletedAt(Instant.now());
                optimRepo.save(optim);

                notificationSvc.pushCvOptimizationEvent(
                    userId, optim.getId(), "rejected",
                    "Profil incompatible avec cette offre — optimisation non réalisable."
                );
                notificationSvc.create(userId, NotificationType.CV_REJECTED,
                    "Optimisation refusée",
                    String.format("« %s » — profil incompatible avec cette offre (%d%% de compatibilité).",
                        optim.getJobTitle(), verdict.score()),
                    "/cv/optimize/" + optim.getId());
                log.info("Optimization {} REJECTED — score {}%, {} → {}",
                    optim.getId(), verdict.score(), verdict.candidateProfile(), verdict.targetProfile());
                return;
            }

            notificationSvc.pushCvOptimizationEvent(userId, optim.getId(), "processing",
                "Profil compatible — réécriture en cours par l'IA…");

            // ── Step 2: Call AI gateway (Gemini → Groq → OpenRouter → Local) ─
            var aiResult    = aiGateway.optimizeCv(cvText, msg.jobDescription());
            String llmResponse = aiResult.content();

            // ── Step 3: Split the response into {atsKeywords, suggestions, optimizedCv}
            String suggestionsJson = extractSuggestions(llmResponse);
            String optimizedCvJson = extractOptimizedCv(llmResponse);
            List<String> atsKeywords = extractAtsKeywords(llmResponse);

            // ── Step 4: Real, job-specific ATS scoring ───────────────────────
            // Score the ORIGINAL CV and the OPTIMIZED CV against the SAME job
            // keywords → an honest, explainable before/after for this offer.
            String optimizedText = flattenCv(optimizedCvJson);
            AtsScorer.JobMatch before = atsScorer.computeJobMatch(cvText, atsKeywords);
            AtsScorer.JobMatch after  = atsScorer.computeJobMatch(optimizedText, atsKeywords);

            // ── Step 5: Persist result ───────────────────────────────────────
            optim.setStatus(CvOptimization.OptimizationStatus.COMPLETED);
            optim.setOriginalScore(before.score());            // job-specific, replaces generic upload score
            optim.setOptimizedScore(after.score());
            optim.setSuggestedChangesJson(suggestionsJson);
            optim.setOptimizedCvJson(optimizedCvJson);
            optim.setMatchedKeywordsJson(toJsonArray(after.matched()));
            optim.setMissingKeywordsJson(toJsonArray(after.missing()));
            optim.setModelUsed(aiResult.provider());
            optim.setProcessingTimeMs(System.currentTimeMillis() - start);
            optim.setCompletedAt(Instant.now());
            optimRepo.save(optim);

            // ── Step 6: Push WebSocket notification + persist a bell notification ─
            String doneMessage = String.format("CV optimisé ! Score ATS : %d%% → %d%% (%d/%d mots-clés)",
                before.score(), after.score(), after.matched().size(),
                after.matched().size() + after.missing().size());
            notificationSvc.pushCvOptimizationEvent(
                userId, optim.getId(), "completed",
                doneMessage,
                aiResult.provider()   // tell Angular which model actually ran
            );
            notificationSvc.create(userId, NotificationType.CV_OPTIMIZED,
                "CV optimisé avec succès",
                String.format("« %s » : score ATS %d%% → %d%%.",
                    optim.getJobTitle(), before.score(), after.score()),
                "/cv/optimize/" + optim.getId());
            log.info("Optimization {} completed in {}ms. ATS {}%→{}%, keywords {}/{} matched",
                optim.getId(), System.currentTimeMillis() - start, before.score(), after.score(),
                after.matched().size(), after.matched().size() + after.missing().size());

        } catch (Exception e) {
            log.error("Optimization {} failed: {}", msg.optimizationId(), e.getMessage(), e);
            optim.setStatus(CvOptimization.OptimizationStatus.FAILED);
            optim.setProcessingTimeMs(System.currentTimeMillis() - start);
            optimRepo.save(optim);
            notificationSvc.pushCvOptimizationEvent(
                userId, optim.getId(), "failed",
                "Tous les services IA sont temporairement indisponibles. Réessayez dans quelques minutes."
            );
            notificationSvc.create(userId, NotificationType.CV_FAILED,
                "Échec de l'optimisation",
                String.format("« %s » : les services IA sont temporairement indisponibles. Réessayez plus tard.",
                    optim.getJobTitle()),
                "/cv/optimize/" + optim.getId());
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
            com.fasterxml.jackson.databind.JsonNode arr =
                root.isArray() ? root : root.has("suggestions") ? root.get("suggestions") : null;
            if (arr == null || !arr.isArray()) return "[]";
            return normalizeSuggestionTypes(arr).toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * The LLM occasionally invents suggestion types outside our 4-value enum
     * (e.g. "summary_rewritten", "contact_info_added"). Map every entry onto a
     * canonical type so the UI styling and the PDF generator always recognise it.
     */
    private com.fasterxml.jackson.databind.JsonNode normalizeSuggestionTypes(
            com.fasterxml.jackson.databind.JsonNode arr) {
        var out = objectMapper.createArrayNode();
        arr.forEach(node -> {
            if (node.isObject()) {
                var obj = (com.fasterxml.jackson.databind.node.ObjectNode) node.deepCopy();
                obj.put("type", canonicalSuggestionType(node.path("type").asText("")));
                out.add(obj);
            } else {
                out.add(node);
            }
        });
        return out;
    }

    private String canonicalSuggestionType(String raw) {
        String t = raw == null ? "" : raw.toLowerCase();
        if (t.equals("keyword_added") || t.equals("section_rewritten")
         || t.equals("format_improved") || t.equals("skill_added")) return t;
        if (t.contains("keyword")) return "keyword_added";
        if (t.contains("skill") || t.contains("competenc")) return "skill_added";
        if (t.contains("rewrit") || t.contains("summary") || t.contains("section") || t.contains("content"))
            return "section_rewritten";
        // contact/format/structure/layout and everything else → format bucket
        return "format_improved";
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

    /** Extract the "atsKeywords" array (the important keywords pulled from the job description). */
    private List<String> extractAtsKeywords(String llmResponse) {
        var root = parseRoot(llmResponse);
        List<String> out = new java.util.ArrayList<>();
        if (root != null && root.has("atsKeywords") && root.get("atsKeywords").isArray()) {
            root.get("atsKeywords").forEach(n -> {
                String s = n.asText("").trim();
                if (!s.isEmpty()) out.add(s);
            });
        }
        return out;
    }

    /** Flatten the structured optimized CV JSON into one text blob for keyword scoring. */
    private String flattenCv(String optimizedCvJson) {
        if (optimizedCvJson == null || optimizedCvJson.isBlank()) return "";
        try {
            var node = objectMapper.readTree(optimizedCvJson);
            // Plain text of every value (summary, skills, competencies, bullets, …)
            StringBuilder sb = new StringBuilder();
            collectText(node, sb);
            return sb.toString();
        } catch (Exception e) {
            return optimizedCvJson; // fall back to raw JSON text — still contains the keywords
        }
    }

    private void collectText(com.fasterxml.jackson.databind.JsonNode node, StringBuilder sb) {
        if (node == null) return;
        if (node.isValueNode()) { sb.append(node.asText()).append(' '); return; }
        node.forEach(child -> collectText(child, sb));
    }

    /** Serialize a list of strings to a JSON array string for storage. */
    private String toJsonArray(List<String> items) {
        try { return objectMapper.writeValueAsString(items); }
        catch (Exception e) { return "[]"; }
    }
}
