package ma.hiresync.cv.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.hiresync.cv.entity.CvOptimization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OptimizationResponse(
        UUID   id,
        String status,
        UUID   cvId,
        String jobId,
        String jobTitle,
        String company,
        int    originalScore,
        int    optimizedScore,
        int    compatibilityScore,
        String rejectionReason,
        String candidateProfile,
        String targetProfile,
        List<String> matchedKeywords,
        List<String> missingKeywords,
        Object suggestedChanges,
        String optimizedCvUrl,
        String modelUsed,
        Long   processingTimeMs,
        Instant createdAt,
        Instant completedAt,
        String  jobUrl,            // link to the real job posting (to apply externally)
        boolean coverLetterReady   // true once a cover letter has been generated & cached
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static OptimizationResponse from(CvOptimization opt, Object changes) {
        return from(opt, changes, null);
    }

    public static OptimizationResponse from(CvOptimization opt, Object changes, String jobUrl) {
        String cvUrl = opt.getOptimizedCvPath() != null
            ? "/api/cv/download/" + opt.getId()
            : null;
        return new OptimizationResponse(
            opt.getId(),
            opt.getStatus().name().toLowerCase(),
            opt.getCv().getId(),
            opt.getJobId(),
            opt.getJobTitle(),
            opt.getCompany(),
            opt.getOriginalScore(),
            opt.getOptimizedScore(),
            opt.getCompatibilityScore(),
            opt.getRejectionReason(),
            opt.getCandidateProfile(),
            opt.getTargetProfile(),
            parseStringArray(opt.getMatchedKeywordsJson()),
            parseStringArray(opt.getMissingKeywordsJson()),
            changes,
            cvUrl,
            opt.getModelUsed(),
            opt.getProcessingTimeMs(),
            opt.getCreatedAt(),
            opt.getCompletedAt(),
            jobUrl,
            opt.getCoverLetterJson() != null && !opt.getCoverLetterJson().isBlank()
        );
    }

    private static List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
