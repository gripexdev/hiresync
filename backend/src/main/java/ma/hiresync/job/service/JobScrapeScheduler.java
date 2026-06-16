package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.job.messaging.ScrapeProducer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes one scrape message per source every 6 hours.
 * ScrapeConsumer picks them up in parallel; EnrichConsumer chains automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobScrapeScheduler {

    private static final long SIX_HOURS_MS = 6 * 60 * 60 * 1000L;

    private final ScrapeProducer scrapeProducer;

    @Scheduled(initialDelay = 60_000, fixedRate = SIX_HOURS_MS)
    public void scheduleScrape() {
        log.info("Scheduled scrape — publishing {} source messages", scrapeProducer.sourceCount());
        scrapeProducer.publishAll();
    }
}
