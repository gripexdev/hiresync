package ma.hiresync.cv.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.auth.repository.UserRepository;
import ma.hiresync.cv.dto.*;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.messaging.OptimizationMessage;
import ma.hiresync.cv.messaging.OptimizationProducer;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
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
    private final CvPdfGenerator           pdfGenerator;
    private final CvTextParser             cvTextParser;
    private final ObjectMapper             objectMapper;
    private final JobRepository            jobRepo;
    private final AiGatewayService         aiGateway;

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
        return OptimizationResponse.from(optim, changes, resolveJobUrl(optim));
    }

    /** Resolve the public link to the original job posting, if the job still exists. */
    private String resolveJobUrl(CvOptimization optim) {
        Job job = findJob(optim.getJobId());
        return job != null ? job.getSourceUrl() : null;
    }

    /** The optimization stores jobId as a String; resolve to a Job when it's a valid UUID. */
    private Job findJob(String jobId) {
        if (jobId == null || jobId.isBlank()) return null;
        try {
            return jobRepo.findById(UUID.fromString(jobId)).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;   // legacy/non-UUID jobId (e.g. test rows)
        }
    }

    // ── GET /api/cv/optimization-history ─────────────────────────────────────
    @Transactional(readOnly = true)
    public List<OptimizationResponse> getHistory(UUID userId) {
        return optimRepo.findByCvUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(o -> OptimizationResponse.from(o, parseChanges(o.getSuggestedChangesJson())))
                .toList();
    }

    // ── GET /api/cv/structured/{optimizationId} ──────────────────────────────
    /**
     * Returns the structured optimized CV (name, summary, experience, skills…)
     * used by the CV Studio to render templates.
     * Falls back to building a basic structure from the original CV text
     * for older optimizations created before structured rebuild existed.
     */
    @Transactional(readOnly = true)
    public Object getStructuredCv(UUID optimizationId, UUID userId) {
        var optim = optimRepo.findByIdAndCvUserId(optimizationId, userId)
            .orElseThrow(() -> new RuntimeException("Optimization not found"));

        // 1. Preferred: the AI-rebuilt structured CV
        if (optim.getOptimizedCvJson() != null && !optim.getOptimizedCvJson().isBlank()) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(optim.getOptimizedCvJson(), Object.class);
            } catch (Exception e) {
                log.warn("Could not parse optimizedCvJson: {}", e.getMessage());
            }
        }

        // 2. Fallback: build a basic structure from the original extracted text
        var cv = cvRepo.findById(optim.getCv().getId()).orElse(null);
        return buildFallbackStructure(cv, optim);
    }

    /** Build a minimal structured CV from the raw extracted text (for legacy records). */
    private Map<String, Object> buildFallbackStructure(Cv cv, CvOptimization optim) {
        String text = (cv != null && cv.getExtractedText() != null) ? cv.getExtractedText() : "";
        // Proper heuristic parser (sections, experience, education, skills, languages)
        return cvTextParser.parse(text, optim.getJobTitle());
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

    // ── GET /api/cv/download/{optimizationId} ────────────────────────────────
    /**
     * Generate the optimized CV PDF on demand.
     * Applies AI suggestions to the original extracted text and renders a PDF.
     */
    @Transactional(readOnly = true)
    public byte[] generateOptimizedCvPdf(UUID optimizationId, UUID userId) throws IOException {
        var optim = optimRepo.findByIdAndCvUserId(optimizationId, userId)
            .orElseThrow(() -> new RuntimeException("Optimization not found"));

        if (optim.getStatus() != CvOptimization.OptimizationStatus.COMPLETED) {
            throw new IllegalStateException("Optimization is not yet completed");
        }

        var cv = cvRepo.findById(optim.getCv().getId())
            .orElseThrow(() -> new RuntimeException("CV not found"));

        return pdfGenerator.generate(cv, optim);
    }

    // ── PATCH /api/cv/optimize/{id}/boost-keywords ────────────────────────────
    /**
     * Inject the user-selected missing keywords into the optimized CV's skills
     * (and coreCompetencies if there is room), then re-run ATS scoring and
     * persist the updated result.
     */
    public OptimizationResponse boostKeywords(UUID optimId, UUID userId, List<String> keywords) {
        var optim = optimRepo.findByIdAndCvUserId(optimId, userId)
            .orElseThrow(() -> new RuntimeException("Optimization not found"));

        if (optim.getStatus() != CvOptimization.OptimizationStatus.COMPLETED)
            throw new IllegalStateException("Keyword boost is only available on completed optimizations");

        if (keywords == null || keywords.isEmpty())
            return OptimizationResponse.from(optim, parseChanges(optim.getSuggestedChangesJson()));

        // 1. Parse the stored optimized CV JSON
        String rawJson = optim.getOptimizedCvJson();
        if (rawJson == null || rawJson.isBlank())
            throw new IllegalStateException("CV data not available for this optimization — it may pre-date structured output.");

        ObjectNode cvNode;
        try {
            JsonNode parsed = objectMapper.readTree(rawJson);
            if (!parsed.isObject())
                throw new IllegalStateException("Optimized CV JSON is not an object");
            cvNode = (ObjectNode) parsed;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse optimized CV: " + e.getMessage());
        }

        // 2. Add keywords to the skills array (case-insensitive dedup)
        Set<String> seenSkills = new HashSet<>();
        ArrayNode skillsArray  = objectMapper.createArrayNode();
        JsonNode  skillsNode   = cvNode.path("skills");
        if (skillsNode.isArray()) {
            skillsNode.forEach(n -> { skillsArray.add(n); seenSkills.add(n.asText("").toLowerCase()); });
        }
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && seenSkills.add(kw.toLowerCase())) {
                skillsArray.add(kw);
            }
        }
        cvNode.set("skills", skillsArray);

        // 3. Mirror the top keywords into coreCompetencies (max 2 additions, cap at 12 total)
        JsonNode coreNode = cvNode.path("coreCompetencies");
        if (coreNode.isArray()) {
            ArrayNode coreArray = (ArrayNode) coreNode;
            Set<String> seenCore = new HashSet<>();
            coreNode.forEach(n -> seenCore.add(n.asText("").toLowerCase()));
            int added = 0;
            for (String kw : keywords) {
                if (added >= 2 || coreArray.size() >= 12) break;
                if (kw != null && !kw.isBlank() && seenCore.add(kw.toLowerCase())) {
                    coreArray.add(kw);
                    added++;
                }
            }
        }

        // 4. Persist updated JSON
        try {
            optim.setOptimizedCvJson(objectMapper.writeValueAsString(cvNode));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize updated CV JSON");
        }

        // 5. Reconstruct the full ATS keyword list (matched + missing = all job keywords)
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(parseJsonStringList(optim.getMatchedKeywordsJson()));
        allKeywords.addAll(parseJsonStringList(optim.getMissingKeywordsJson()));

        // 6. Flatten the updated CV JSON → plain text and re-score
        StringBuilder flat = new StringBuilder();
        collectNodeText(cvNode, flat);
        AtsScorer.JobMatch match = atsScorer.computeJobMatch(flat.toString(), allKeywords);

        optim.setOptimizedScore(match.score());
        optim.setMatchedKeywordsJson(toJsonString(match.matched()));
        optim.setMissingKeywordsJson(toJsonString(match.missing()));
        optimRepo.save(optim);

        log.info("Keyword boost for optimization {} — {} added, score {}%→{}%",
            optimId, keywords.size(), optim.getOriginalScore(), match.score());

        return OptimizationResponse.from(optim, parseChanges(optim.getSuggestedChangesJson()));
    }

    // ── POST /api/cv/optimize/{id}/cover-letter ───────────────────────────────
    /**
     * Generate (or return the cached) cover letter / application email for a
     * completed optimization. The letter is grounded in the optimized CV + the
     * original job posting, and cached on the optimization after first generation.
     */
    public CoverLetterResponse generateCoverLetter(UUID optimId, UUID userId, boolean regenerate) {
        var optim = optimRepo.findByIdAndCvUserId(optimId, userId)
            .orElseThrow(() -> new RuntimeException("Optimization not found"));

        if (optim.getStatus() != CvOptimization.OptimizationStatus.COMPLETED)
            throw new IllegalStateException("La lettre n'est disponible qu'après une optimisation réussie.");

        // Return cached unless a regeneration was explicitly requested
        if (!regenerate && optim.getCoverLetterJson() != null && !optim.getCoverLetterJson().isBlank()) {
            return parseCoverLetter(optim.getCoverLetterJson(), optim.getModelUsed());
        }

        // Build the source CV text from the optimized structured CV (best tailored version)
        String cvText = optim.getOptimizedCvJson();
        if (cvText == null || cvText.isBlank()) {
            var cv = cvRepo.findById(optim.getCv().getId()).orElse(null);
            cvText = cv != null && cv.getExtractedText() != null ? cv.getExtractedText() : "";
        }

        Job job = findJob(optim.getJobId());
        String company = job != null && job.getCompany() != null ? job.getCompany() : optim.getCompany();
        String jobDesc = job != null && job.getDescription() != null ? job.getDescription() : "";

        var result = aiGateway.generateCoverLetter(cvText, optim.getJobTitle(), company, jobDesc);

        // Normalise to a clean {subject, body} JSON and cache it
        CoverLetterResponse letter = parseCoverLetter(result.content(), result.provider());
        try {
            optim.setCoverLetterJson(objectMapper.writeValueAsString(
                Map.of("subject", letter.subject(), "body", letter.body())));
            optimRepo.save(optim);
        } catch (Exception e) {
            log.warn("Could not cache cover letter for {}: {}", optimId, e.getMessage());
        }
        log.info("Cover letter generated for optimization {} (provider {})", optimId, result.provider());
        return letter;
    }

    /** Parse the LLM/cover-letter JSON (strips markdown fences) into a response. */
    private CoverLetterResponse parseCoverLetter(String raw, String provider) {
        String json = raw == null ? "" : raw.trim()
            .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
            .replaceAll("(?s)\\s*```$", "").trim();
        try {
            JsonNode n = objectMapper.readTree(json);
            String subject = n.path("subject").asText("Candidature");
            String body    = n.path("body").asText("");
            if (body.isBlank()) body = json;   // model returned prose, not JSON — use as-is
            return new CoverLetterResponse(subject, body, provider);
        } catch (Exception e) {
            // Not JSON — treat the whole thing as the body
            return new CoverLetterResponse("Candidature", json, provider);
        }
    }

    private List<String> parseJsonStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private String toJsonString(List<String> items) {
        try { return objectMapper.writeValueAsString(items); }
        catch (Exception e) { return "[]"; }
    }

    private void collectNodeText(JsonNode node, StringBuilder sb) {
        if (node == null) return;
        if (node.isValueNode()) { sb.append(node.asText()).append(' '); return; }
        node.forEach(child -> collectNodeText(child, sb));
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
