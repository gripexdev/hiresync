package ma.hiresync.job.controller;

import lombok.RequiredArgsConstructor;
import ma.hiresync.job.dto.EnrichTriggerResponse;
import ma.hiresync.job.dto.FacetResponse;
import ma.hiresync.job.dto.JobResponse;
import ma.hiresync.job.dto.ScrapeTriggerResponse;
import ma.hiresync.job.filter.JobSearchCriteria;
import ma.hiresync.job.service.JobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /** GET /api/jobs?q=&city=&contractType=&experienceLevel=&sector=&page=0&size=20 */
    @GetMapping("/jobs")
    public Page<JobResponse> search(
            @RequestParam(defaultValue = "")  String q,
            @RequestParam(required = false)   String city,
            @RequestParam(required = false)   String contractType,
            @RequestParam(required = false)   String experienceLevel,
            @RequestParam(required = false)   String sector,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var criteria = new JobSearchCriteria(q, city, contractType, experienceLevel, sector);
        return jobService.search(criteria, PageRequest.of(page, size, Sort.by("scrapedAt").descending()));
    }

    /** GET /api/jobs/facets — live filter options with counts, derived from the data */
    @GetMapping("/jobs/facets")
    public FacetResponse facets() {
        return jobService.facets();
    }

    /** GET /api/jobs/{id} */
    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getById(@PathVariable UUID id) {
        return jobService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/admin/scrape/trigger — runs scraper synchronously */
    @PostMapping("/admin/scrape/trigger")
    public ResponseEntity<ScrapeTriggerResponse> trigger() {
        return ResponseEntity.ok(jobService.triggerScrape());
    }

    /**
     * POST /api/admin/enrich/trigger
     * Fetches detail pages for up to 20 unenriched jobs and fills in
     * full description + requirements. Call repeatedly until enrichedLeft = 0.
     */
    @PostMapping("/admin/enrich/trigger")
    public ResponseEntity<EnrichTriggerResponse> triggerEnrich() {
        return ResponseEntity.ok(jobService.triggerEnrich());
    }
}
