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
 * IndeedScraperService extracts job cards from a JSON object embedded in a
 * <script> tag (window.mosaic.providerData[...]) rather than from rendered
 * HTML. We exercise parsePage(Document) directly via reflection with a
 * fixture matching that exact marker + balanced-braces JSON shape.
 */
@ExtendWith(MockitoExtension.class)
class IndeedScraperServiceTest {

    @Mock private JobRepository jobRepository;

    private IndeedScraperService scraper() {
        return new IndeedScraperService(jobRepository);
    }

    private int parsePage(IndeedScraperService scraper, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Method m = IndeedScraperService.class.getDeclaredMethod("parsePage", Document.class);
        m.setAccessible(true);
        return (int) m.invoke(scraper, doc);
    }

    private static String htmlWithJobCards(String resultsJsonArray) {
        String json = "{\"metaData\":{\"mosaicProviderJobCardsModel\":{\"results\":" + resultsJsonArray + "}}}";
        return "<html><body><script>window.mosaic.providerData[\"mosaic-provider-jobcards\"]=" + json
                + ";window.mosaic.providerData[\"other\"]={};</script></body></html>";
    }

    @Test
    void parsePage_validResult_extractsCoreFields() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);
        String results = """
                [{"jobkey":"abc123","title":"Développeur Backend","company":"Acme",
                  "formattedLocation":"Casablanca","jobTypes":["CDI"],
                  "snippet":"<ul><li>Java</li><li>Spring</li></ul>"}]
                """;

        int saved = parsePage(scraper(), htmlWithJobCards(results));

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        Job job = captor.getValue().get(0);

        assertThat(job.getTitle()).isEqualTo("Développeur Backend");
        assertThat(job.getCompany()).isEqualTo("Acme");
        assertThat(job.getLocation()).isEqualTo("Casablanca");
        assertThat(job.getContractType()).isEqualTo("CDI");
        assertThat(job.getSourceUrl()).contains("vjk=abc123");
        assertThat(job.getDescription()).contains("- Java").contains("- Spring");
    }

    @Test
    void parsePage_missingJobkey_isSkipped() throws Exception {
        String results = "[{\"title\":\"Sans jobkey\"}]";

        int saved = parsePage(scraper(), htmlWithJobCards(results));

        assertThat(saved).isEqualTo(0);
    }

    @Test
    void parsePage_alreadyKnownJob_isSkippedByDedupKey() throws Exception {
        when(jobRepository.existsBySourceUrl(contains("vjk=abc123"))).thenReturn(true);
        String results = "[{\"jobkey\":\"abc123\",\"title\":\"Dev\"}]";

        int saved = parsePage(scraper(), htmlWithJobCards(results));

        assertThat(saved).isEqualTo(0);
    }

    @Test
    void parsePage_noMarkerInAnyScriptTag_returnsZero() throws Exception {
        int saved = parsePage(scraper(), "<html><body><script>console.log('nothing here');</script></body></html>");

        assertThat(saved).isEqualTo(0);
        verify(jobRepository, never()).saveAll(any());
    }

    @Test
    void parsePage_contractTypeFallsBackToTaxonomyAttributesWhenJobTypesEmpty() throws Exception {
        when(jobRepository.existsBySourceUrl(any())).thenReturn(false);
        String results = """
                [{"jobkey":"xyz","title":"Dev","jobTypes":[],
                  "taxonomyAttributes":[{"label":"job-types","attributes":[{"label":"Temps plein"}]}]}]
                """;

        parsePage(scraper(), htmlWithJobCards(results));

        ArgumentCaptor<List<Job>> captor = ArgumentCaptor.forClass(List.class);
        verify(jobRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getContractType()).isEqualTo("Temps plein");
    }
}
