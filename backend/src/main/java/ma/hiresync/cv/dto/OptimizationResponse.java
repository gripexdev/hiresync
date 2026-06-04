package ma.hiresync.cv.dto;

import ma.hiresync.cv.entity.CvOptimization;

import java.time.Instant;
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
        Object suggestedChanges,
        String optimizedCvUrl,
        String modelUsed,
        Long   processingTimeMs,
        Instant createdAt,
        Instant completedAt
) {
    public static OptimizationResponse from(CvOptimization opt, Object changes) {
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
            changes,
            cvUrl,
            opt.getModelUsed(),
            opt.getProcessingTimeMs(),
            opt.getCreatedAt(),
            opt.getCompletedAt()
        );
    }
}
