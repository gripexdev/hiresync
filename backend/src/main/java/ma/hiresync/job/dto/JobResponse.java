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
        String logo = job.getLogoUrl() != null ? job.getLogoUrl() : sourceFallbackLogo(job.getSource());
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
                logo,
                job.getSourceUrl(),
                job.getSource(),
                job.getPostedAt(),
                job.getScrapedAt()
        );
    }

    private static String sourceFallbackLogo(String source) {
        if (source == null) return null;
        // Strip subdomain to get the root domain for the favicon lookup
        String domain = source.replaceFirst("^(?:www\\.)?", "");
        return "https://www.google.com/s2/favicons?sz=64&domain=" + domain;
    }
}
