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
class EmploiMaScraperServiceTest {

    @Mock private JobRepository jobRepository;

    private EmploiMaScraperService scraper() {
        return new EmploiMaScraperService(jobRepository);
    }

    private int parsePage(EmploiMaScraperService scraper, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Method m = EmploiMaScraperService.class.getDeclaredMethod("parsePage", Document.class);
        m.setAccessible(true);
        return (int) m.invoke(scraper, doc);
    }

    private static final String CARD_HTML = """
            <div class="card card-job featured" data-href="https://www.emploi.ma/offre-emploi-maroc/dev-backend-1">
              <h3><a href="/offre-emploi-maroc/dev-backend-1">Développeur Backend</a></h3>
              <a href="/recruteur/acme" class="card-job-company company-name">Acme</a>
              <picture><img src="/logos/acme.png"></picture>
              <div class="card-job-description"><p>Recherche développeur Java confirmé.</p></div>
              <time datetime="2026-06-10">10.06.2026</time>
              <ul>
                <li>Contrat proposé : <strong>CDI</strong></li>
                <li>Niveau d'expérience : <strong>Confirmé</strong></li>
                <li>Région de : <strong>Casablanca</strong></li>
              </ul>
            </div>
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
        assertThat(job.getCompany()).isEqualTo("Acme");
        assertThat(job.getLogoUrl()).isEqualTo("/logos/acme.png");
        assertThat(job.getDescription()).contains("Java confirmé");
        assertThat(job.getContractType()).isEqualTo("CDI");
        assertThat(job.getExperienceLevel()).isEqualTo("Confirmé");
        assertThat(job.getLocation()).isEqualTo("Casablanca");
        assertThat(job.getPostedAt()).isEqualTo(java.time.LocalDate.of(2026, 6, 10).atStartOfDay());
    }

    @Test
    void parsePage_placeholderLogo_isExcluded() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);
        String html = """
                <div class="card card-job" data-href="https://www.emploi.ma/offre/2">
                  <h3><a href="/offre/2">Poste</a></h3>
                  <picture><img src="/img/logo-non-dispo.jpg"></picture>
                </div>
                """;

        parsePage(scraper(), html);

        ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getLogoUrl()).isNull();
    }

    @Test
    void parsePage_missingDataHrefAttribute_isSkipped() throws Exception {
        String html = "<div class=\"card card-job\"><h3><a href=\"/x\">Sans data-href</a></h3></div>";

        int saved = parsePage(scraper(), html);

        assertThat(saved).isEqualTo(0);
    }

    @Test
    void parsePage_noCards_returnsZeroWithoutSaving() throws Exception {
        int saved = parsePage(scraper(), "<html><body>Aucun résultat</body></html>");

        assertThat(saved).isEqualTo(0);
        verify(jobRepository, never()).saveAll(any());
    }
}
