package ma.hiresync.job.dto;

import ma.hiresync.job.entity.Job;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Public-facing shape of a {@link Job}. Keeps the JPA entity from leaking
 * through the API — mirrors the cv.dto.CvResponse pattern.
 */
public record JobResponse(
        UUID    id,
        String  title,
        String  company,
        String  location,
        String  contractType,
        String  sector,
        String  experienceLevel,
        String  description,
        List<String> requirements,
        String  logoUrl,
        String  sourceUrl,
        String  source,
        LocalDateTime postedAt,
        LocalDateTime scrapedAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getContractType(),
                job.getSector(),
                job.getExperienceLevel(),
                job.getDescription(),
                job.getRequirements(),
                job.getLogoUrl(),
                job.getSourceUrl(),
                job.getSource(),
                job.getPostedAt(),
                job.getScrapedAt()
        );
    }
}
