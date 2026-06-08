package ma.hiresync.job;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository      jobRepository;
    private final JobScraperService  scraper;
    private final JobEnrichmentService enricher;

    /** GET /api/jobs?q=&page=0&size=20 */
    @GetMapping("/jobs")
    public Page<Job> search(
            @RequestParam(defaultValue = "")  String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return jobRepository.search(q, PageRequest.of(page, size, Sort.by("scrapedAt").descending()));
    }

    /** GET /api/jobs/{id} */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<Job> getById(@PathVariable UUID id) {
        return jobRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/admin/scrape/trigger — runs scraper synchronously */
    @PostMapping("/admin/scrape/trigger")
    public ResponseEntity<Map<String, Object>> trigger() {
        int saved = scraper.scrape();
        long total = jobRepository.count();
        return ResponseEntity.ok(Map.of(
                "newJobsSaved", saved,
                "totalJobsInDb", total
        ));
    }

    /**
     * POST /api/admin/enrich/trigger
     * Fetches detail pages for up to 20 unenriched jobs and fills in
     * full description + requirements. Call repeatedly until enrichedLeft = 0.
     */
    @PostMapping("/admin/enrich/trigger")
    public ResponseEntity<Map<String, Object>> triggerEnrich() {
        int enriched     = enricher.enrich();
        long totalDone   = jobRepository.countByEnrichedTrue();
        long totalJobs   = jobRepository.count();
        return ResponseEntity.ok(Map.of(
                "enrichedThisRun", enriched,
                "totalEnriched",   totalDone,
                "enrichedLeft",    totalJobs - totalDone
        ));
    }
}
