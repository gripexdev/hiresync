package ma.hiresync.job.messaging;

import ma.hiresync.job.service.EmploiMaScraperService;
import ma.hiresync.job.service.IndeedScraperService;
import ma.hiresync.job.service.JobScraperService;
import ma.hiresync.job.service.LinkedInScraperService;
import ma.hiresync.job.service.MarocEmploiScraperService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScrapeConsumerTest {

    @Mock private JobScraperService rekruteScraper;
    @Mock private EmploiMaScraperService emploiMaScraper;
    @Mock private IndeedScraperService indeedScraper;
    @Mock private LinkedInScraperService linkedInScraper;
    @Mock private MarocEmploiScraperService marocEmploiScraper;
    @Mock private EnrichProducer enrichProducer;

    private ScrapeConsumer consumer() {
        return new ScrapeConsumer(rekruteScraper, emploiMaScraper, indeedScraper,
                linkedInScraper, marocEmploiScraper, enrichProducer);
    }

    @Test
    void consume_rekrute_routesToTheRekruteScraperOnly() {
        when(rekruteScraper.scrape()).thenReturn(5);

        consumer().consume(new ScrapeMessage("rekrute.com"));

        verify(rekruteScraper).scrape();
        verifyNoInteractions(emploiMaScraper, indeedScraper, linkedInScraper, marocEmploiScraper);
    }

    @Test
    void consume_emploiMa_routesToTheEmploiMaScraperOnly() {
        when(emploiMaScraper.scrape()).thenReturn(3);
        consumer().consume(new ScrapeMessage("emploi.ma"));
        verify(emploiMaScraper).scrape();
        verifyNoInteractions(rekruteScraper, indeedScraper, linkedInScraper, marocEmploiScraper);
    }

    @Test
    void consume_indeed_routesToTheIndeedScraperOnly() {
        when(indeedScraper.scrape()).thenReturn(2);
        consumer().consume(new ScrapeMessage("indeed.ma"));
        verify(indeedScraper).scrape();
    }

    @Test
    void consume_linkedin_routesToTheLinkedInScraperOnly() {
        when(linkedInScraper.scrape()).thenReturn(4);
        consumer().consume(new ScrapeMessage("linkedin.com"));
        verify(linkedInScraper).scrape();
    }

    @Test
    void consume_marocemploi_routesToTheMarocEmploiScraperOnly() {
        when(marocEmploiScraper.scrape()).thenReturn(1);
        consumer().consume(new ScrapeMessage("marocemploi.net"));
        verify(marocEmploiScraper).scrape();
    }

    @Test
    void consume_unknownSource_callsNoScraperButStillChainsEnrichment() {
        consumer().consume(new ScrapeMessage("unknown-source.com"));

        verifyNoInteractions(rekruteScraper, emploiMaScraper, indeedScraper, linkedInScraper, marocEmploiScraper);
        verify(enrichProducer).publish("unknown-source.com");
    }

    @Test
    void consume_alwaysChainsEnrichmentRegardlessOfSavedCount() {
        when(rekruteScraper.scrape()).thenReturn(0);

        consumer().consume(new ScrapeMessage("rekrute.com"));

        verify(enrichProducer).publish("rekrute.com");
    }
}
