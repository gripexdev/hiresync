package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import ma.hiresync.job.dto.EnrichTriggerResponse;
import ma.hiresync.job.dto.JobResponse;
import ma.hiresync.job.dto.ScrapeTriggerResponse;
import ma.hiresync.job.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the job domain — coordinates the repository and the
 * scraping/enrichment services, and maps entities to DTOs. Mirrors the role
 * CvService plays for the cv domain: the controller talks only to this class.
 */
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository          jobRepository;
    private final JobScraperService      rekruteScraper;
    private final EmploiMaScraperService emploiMaScraper;
    private final IndeedScraperService   indeedScraper;
    private final LinkedInScraperService linkedInScraper;
    private final JobEnrichmentService   enricher;

    /** Paginated job search (empty {@code q} returns everything). */
    public Page<JobResponse> search(String q, Pageable pageable) {
        return jobRepository.search(q, pageable).map(JobResponse::from);
    }

    public Optional<JobResponse> getById(UUID id) {
        return jobRepository.findById(id).map(JobResponse::from);
    }

    /** Runs all scrapers synchronously and reports how many new jobs were saved. */
    public ScrapeTriggerResponse triggerScrape() {
        int saved = rekruteScraper.scrape() + emploiMaScraper.scrape() + indeedScraper.scrape() + linkedInScraper.scrape();
        long total = jobRepository.count();
        return new ScrapeTriggerResponse(saved, total);
    }

    /** Enriches up to 20 unenriched jobs and reports progress. */
    public EnrichTriggerResponse triggerEnrich() {
        int enriched   = enricher.enrich();
        long totalDone = jobRepository.countByEnrichedTrue();
        long totalJobs = jobRepository.count();
        return new EnrichTriggerResponse(enriched, totalDone, totalJobs - totalDone);
    }
}
