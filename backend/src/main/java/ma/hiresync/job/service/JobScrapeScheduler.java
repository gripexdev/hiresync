package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the scrape + enrich pipeline automatically — no admin click needed.
 *
 * Both steps are safe to run repeatedly: scraping is idempotent (deduped by
 * {@code sourceUrl}), and enrichment only touches jobs where {@code enriched = false}.
 * Per-source/per-job failures are already swallowed inside
 * {@link JobService#triggerScrape()} / {@link JobService#triggerEnrich()}, so one
 * bad page never blocks the others or the next scheduled run.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobScrapeScheduler {

    // Hard cap on enrich rounds per run, in case a source keeps failing without
    // ever marking jobs enriched — prevents an unbounded loop.
    private static final int MAX_ENRICH_ROUNDS = 25;

    private static final long SIX_HOURS_MS = 6 * 60 * 60 * 1000L;

    private final JobService jobService;

    /** ~1 minute after startup, then every 6 hours. */
    @Scheduled(initialDelay = 60_000, fixedRate = SIX_HOURS_MS)
    public void scrapeAndEnrich() {
        log.info("Scheduled scrape+enrich run starting");

        try {
            var scrapeResult = jobService.triggerScrape();
            log.info("Scheduled scrape: {} new job(s) saved (total in DB: {})",
                    scrapeResult.newJobsSaved(), scrapeResult.totalJobsInDb());
        } catch (Exception e) {
            log.error("Scheduled scrape failed: {}", e.getMessage(), e);
        }

        try {
            long enrichedLeft;
            int round = 0;
            do {
                var enrichResult = jobService.triggerEnrich();
                enrichedLeft = enrichResult.enrichedLeft();
                round++;
                log.info("Scheduled enrichment round {}: {} job(s) enriched, {} left",
                        round, enrichResult.enrichedThisRun(), enrichedLeft);
            } while (enrichedLeft > 0 && round < MAX_ENRICH_ROUNDS);
        } catch (Exception e) {
            log.error("Scheduled enrichment failed: {}", e.getMessage(), e);
        }

        log.info("Scheduled scrape+enrich run complete");
    }
}
