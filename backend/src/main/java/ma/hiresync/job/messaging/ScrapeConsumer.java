package ma.hiresync.job.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.config.RabbitMQConfig;
import ma.hiresync.job.service.EmploiMaScraperService;
import ma.hiresync.job.service.IndeedScraperService;
import ma.hiresync.job.service.JobScraperService;
import ma.hiresync.job.service.LinkedInScraperService;
import ma.hiresync.job.service.MarocEmploiScraperService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes one scrape message per source.
 * concurrency=5 means all 5 sources can run simultaneously on separate threads.
 * After scraping, publishes an enrich message so new jobs are enriched automatically.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScrapeConsumer {

    private final JobScraperService         rekruteScraper;
    private final EmploiMaScraperService    emploiMaScraper;
    private final IndeedScraperService      indeedScraper;
    private final LinkedInScraperService    linkedInScraper;
    private final MarocEmploiScraperService marocEmploiScraper;
    private final EnrichProducer            enrichProducer;

    @RabbitListener(queues = RabbitMQConfig.JOB_SCRAPE_QUEUE, concurrency = "5")
    public void consume(ScrapeMessage msg) {
        log.info("[scrape] Starting source: {}", msg.source());
        long start = System.currentTimeMillis();

        int saved = switch (msg.source()) {
            case "rekrute.com"     -> rekruteScraper.scrape();
            case "emploi.ma"       -> emploiMaScraper.scrape();
            case "indeed.ma"       -> indeedScraper.scrape();
            case "linkedin.com"    -> linkedInScraper.scrape();
            case "marocemploi.net" -> marocEmploiScraper.scrape();
            default -> {
                log.warn("[scrape] Unknown source: {}", msg.source());
                yield 0;
            }
        };

        long ms = System.currentTimeMillis() - start;
        log.info("[scrape] {} → {} new jobs saved in {}ms", msg.source(), saved, ms);

        // Chain: trigger enrichment for the new jobs just inserted
        enrichProducer.publish(msg.source());
    }
}
