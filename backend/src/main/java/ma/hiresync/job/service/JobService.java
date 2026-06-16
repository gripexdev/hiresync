package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import ma.hiresync.job.dto.EnrichTriggerResponse;
import ma.hiresync.job.dto.FacetResponse;
import ma.hiresync.job.dto.JobResponse;
import ma.hiresync.job.dto.ScrapeTriggerResponse;
import ma.hiresync.job.filter.JobFilterCatalog;
import ma.hiresync.job.filter.JobSearchCriteria;
import ma.hiresync.job.filter.JobSpecifications;
import ma.hiresync.job.messaging.ScrapeProducer;
import ma.hiresync.job.repository.JobRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    private final JobRepository    jobRepository;
    private final ScrapeProducer   scrapeProducer;
    private final JobEnrichmentService enricher;

    /** Paginated, filtered job search. Empty criteria returns everything. */
    public Page<JobResponse> search(JobSearchCriteria criteria, Pageable pageable) {
        return jobRepository.findAll(JobSpecifications.build(criteria), pageable).map(JobResponse::from);
    }

    public Optional<JobResponse> getById(UUID id) {
        return jobRepository.findById(id).map(JobResponse::from);
    }

    /**
     * Live filter options with counts, computed from the actual data: each
     * bucket's count is the number of jobs whose raw field matches any of the
     * bucket's keywords. Buckets with zero matches are omitted so the dropdowns
     * never offer a dead option.
     */
    public FacetResponse facets() {
        return new FacetResponse(
                buildFacets(JobFilterCatalog.CITY,       jobRepository.countByLocation()),
                buildFacets(JobFilterCatalog.CONTRACT,   jobRepository.countByContractType()),
                buildFacets(JobFilterCatalog.EXPERIENCE, jobRepository.countByExperience()),
                buildFacets(JobFilterCatalog.SECTOR,     jobRepository.countBySector())
        );
    }

    /**
     * @param rawCounts rows of {@code [rawValue (nullable String), count (Number)]}
     *                  straight from a {@code GROUP BY} query.
     */
    private List<FacetResponse.Facet> buildFacets(List<JobFilterCatalog.Bucket> buckets, List<Object[]> rawCounts) {
        List<FacetResponse.Facet> facets = new ArrayList<>();
        for (JobFilterCatalog.Bucket bucket : buckets) {
            long total = 0;
            for (Object[] row : rawCounts) {
                String raw = row[0] == null ? "" : ((String) row[0]).toLowerCase();
                long count = ((Number) row[1]).longValue();
                for (String kw : bucket.keywords()) {
                    if (raw.contains(kw.toLowerCase())) {
                        total += count;
                        break; // count each raw value once per bucket
                    }
                }
            }
            if (total > 0) {
                facets.add(new FacetResponse.Facet(bucket.key(), bucket.label(), total));
            }
        }
        facets.sort(Comparator.comparingLong(FacetResponse.Facet::count).reversed());
        return facets;
    }

    /** Queues one scrape message per source — all 5 run in parallel via RabbitMQ. */
    public ScrapeTriggerResponse triggerScrape() {
        scrapeProducer.publishAll();
        return new ScrapeTriggerResponse(scrapeProducer.sourceCount(), jobRepository.count());
    }

    /** Enriches up to 20 unenriched jobs and reports progress. */
    public EnrichTriggerResponse triggerEnrich() {
        int enriched   = enricher.enrich();
        long totalDone = jobRepository.countByEnrichedTrue();
        long totalJobs = jobRepository.count();
        return new EnrichTriggerResponse(enriched, totalDone, totalJobs - totalDone);
    }
}
