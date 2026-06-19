package ma.hiresync.application.dto;

import ma.hiresync.application.entity.JobApplication;

import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID    id,
        UUID    jobId,
        String  jobTitle,
        String  company,
        String  location,
        UUID    cvId,
        String  cvFileName,
        String  status,
        String  coverNote,
        Integer matchScore,
        Instant appliedAt,
        Instant updatedAt
) {
    public static ApplicationResponse from(JobApplication a) {
        return from(a, a.getMatchScore());
    }

    /** Variant allowing a live/recomputed match score to override the stored one. */
    public static ApplicationResponse from(JobApplication a, Integer matchScore) {
        return new ApplicationResponse(
            a.getId(),
            a.getJobId(),
            a.getJobTitle(),
            a.getCompany(),
            a.getLocation(),
            a.getCvId(),
            a.getCvFileName(),
            a.getStatus().name().toLowerCase(),
            a.getCoverNote(),
            matchScore,
            a.getAppliedAt(),
            a.getUpdatedAt()
        );
    }
}
