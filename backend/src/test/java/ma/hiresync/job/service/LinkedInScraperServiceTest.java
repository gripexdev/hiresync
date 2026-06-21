package ma.hiresync.job.service;

import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkedInScraperServiceTest {

    @Mock private JobRepository jobRepository;

    private LinkedInScraperService scraper() {
        return new LinkedInScraperService(jobRepository);
    }

    private int parsePage(LinkedInScraperService scraper, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Method m = LinkedInScraperService.class.getDeclaredMethod("parsePage", Document.class);
        m.setAccessible(true);
        return (int) m.invoke(scraper, doc);
    }

    private static final String CARD_HTML = """
            <div class="base-search-card">
              <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/12345?position=1&pageNum=0&refId=abc"></a>
              <h3 class="base-search-card__title">Développeur Backend</h3>
              <h4 class="base-search-card__subtitle">Acme</h4>
              <span class="job-search-card__location">Casablanca, Maroc</span>
              <time class="job-search-card__listdate" datetime="2026-06-08">il y a 2 jours</time>
              <img class="artdeco-entity-image" data-delayed-url="https://logo.acme/img.png">
            </div>
            """;

    @Test
    void parsePage_validCard_extractsAllFieldsAndStripsTrackingParams() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);

        int saved = parsePage(scraper(), CARD_HTML);

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        Job job = captor.getValue().get(0);

        assertThat(job.getTitle()).isEqualTo("Développeur Backend");
        assertThat(job.getCompany()).isEqualTo("Acme");
        assertThat(job.getLocation()).isEqualTo("Casablanca, Maroc");
        assertThat(job.getSourceUrl()).isEqualTo("https://www.linkedin.com/jobs/view/12345"); // tracking params stripped
        assertThat(job.getLogoUrl()).isEqualTo("https://logo.acme/img.png");
        assertThat(job.getPostedAt()).isEqualTo(java.time.LocalDate.of(2026, 6, 8).atStartOfDay());
    }

    @Test
    void parsePage_alreadyKnownStrippedUrl_isSkipped() throws Exception {
        when(jobRepository.existsBySourceUrl("https://www.linkedin.com/jobs/view/12345")).thenReturn(true);

        int saved = parsePage(scraper(), CARD_HTML);

        assertThat(saved).isEqualTo(0);
    }

    @Test
    void parsePage_missingTitleElement_isSkipped() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);
        String html = """
                <div class="base-search-card">
                  <a class="base-card__full-link" href="https://www.linkedin.com/jobs/view/999"></a>
                </div>
                """;

        int saved = parsePage(scraper(), html);

        assertThat(saved).isEqualTo(0);
    }

    @Test
    void parsePage_noCards_returnsZero() throws Exception {
        int saved = parsePage(scraper(), "<html><body>Empty</body></html>");
        assertThat(saved).isEqualTo(0);
    }
}
