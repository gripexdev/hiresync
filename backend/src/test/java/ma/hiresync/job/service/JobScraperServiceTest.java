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

/**
 * Tests the rekrute.com HTML-parsing logic in isolation, without any network
 * call: parsePage(Document) is invoked directly via reflection (it's private
 * by design — production code never needs to call it from outside scrape())
 * on a Document built in-memory from a hand-crafted HTML fixture that mirrors
 * rekrute.com's real markup, as documented in the selectors used by the class
 * itself.
 */
@ExtendWith(MockitoExtension.class)
class JobScraperServiceTest {

    @Mock private JobRepository jobRepository;

    private JobScraperService scraper() {
        return new JobScraperService(jobRepository);
    }

    private int parsePage(JobScraperService scraper, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Method m = JobScraperService.class.getDeclaredMethod("parsePage", Document.class);
        m.setAccessible(true);
        return (int) m.invoke(scraper, doc);
    }

    private static final String CARD_HTML = """
            <html><body>
            <li class="post-id" id="183362">
              <a class="titreJob" href="/offre/dev-backend-183362.html">Développeur Backend | Casablanca (Maroc)</a>
              <img class="photo" src="/rekrute/file/jobOfferLogo/jobOfferId/183362" alt="Acme Corp">
              <div class="info"><span>Recherche développeur Java Spring expérimenté.</span></div>
              <em class="date"><span>06/06/2026</span></em>
              <a href="?contractType=cdi">CDI</a>
              <a href="?sectorId=it">Informatique</a>
              <a href="?workExperienceId=confirme">Confirmé</a>
            </li>
            </body></html>
            """;

    @Test
    void parsePage_validCard_extractsAllFieldsCorrectly() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);

        int saved = parsePage(scraper(), CARD_HTML);

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        Job job = captor.getValue().get(0);

        assertThat(job.getTitle()).isEqualTo("Développeur Backend");
        assertThat(job.getLocation()).isEqualTo("Casablanca");
        assertThat(job.getCompany()).isEqualTo("Acme Corp");
        assertThat(job.getSourceUrl()).isEqualTo("https://www.rekrute.com/offre/dev-backend-183362.html");
        assertThat(job.getSource()).isEqualTo("rekrute.com");
        assertThat(job.getDescription()).contains("Java Spring");
        assertThat(job.getContractType()).isEqualTo("CDI");
        assertThat(job.getSector()).isEqualTo("Informatique");
        assertThat(job.getExperienceLevel()).isEqualTo("Confirmé");
        assertThat(job.getPostedAt()).isEqualTo(java.time.LocalDate.of(2026, 6, 6).atStartOfDay());
    }

    @Test
    void parsePage_alreadyKnownSourceUrl_isSkipped() throws Exception {
        when(jobRepository.existsBySourceUrl("https://www.rekrute.com/offre/dev-backend-183362.html")).thenReturn(true);

        int saved = parsePage(scraper(), CARD_HTML);

        assertThat(saved).isEqualTo(0);
        verify(jobRepository).saveAll(List.of());
    }

    @Test
    void parsePage_confidentialCompany_leavesCompanyAndLogoNull() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);
        String html = """
                <li class="post-id" id="1">
                  <a class="titreJob" href="/offre/confidentiel.html">Poste confidentiel | Rabat (Maroc)</a>
                  <img class="photo" src="/rekrute/file/jobOfferLogo/confidentiel.png" alt="">
                </li>
                """;

        parsePage(scraper(), html);

        ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getCompany()).isNull();
        assertThat(captor.getValue().get(0).getLogoUrl()).isNull();
    }

    @Test
    void parsePage_noCardsOnPage_savesNothingAndReturnsZero() throws Exception {
        int saved = parsePage(scraper(), "<html><body><p>Aucune offre trouvée</p></body></html>");

        assertThat(saved).isEqualTo(0);
        verify(jobRepository, never()).saveAll(any());
    }

    @Test
    void parsePage_cardWithoutTitleLink_isSkippedWithoutError() throws Exception {
        String html = "<li class=\"post-id\" id=\"2\"></li>";

        int saved = parsePage(scraper(), html);

        assertThat(saved).isEqualTo(0);
    }
}
