package ma.hiresync.job.service;

import ma.hiresync.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarocEmploiScraperService fetches both the listing and, inline, each job's
 * detail page through FlareSolverr — that network coupling makes parseCard()
 * impractical to unit-test without a much heavier integration harness. The
 * date parsing and logo-cleaning helpers, however, are pure functions and are
 * exercised directly here via reflection.
 */
@ExtendWith(MockitoExtension.class)
class MarocEmploiScraperServiceTest {

    @Mock private JobRepository jobRepository;

    private MarocEmploiScraperService scraper() {
        return new MarocEmploiScraperService(jobRepository);
    }

    private Object invoke(String method, Class<?> paramType, Object arg) throws Exception {
        Method m = MarocEmploiScraperService.class.getDeclaredMethod(method, paramType);
        m.setAccessible(true);
        return m.invoke(scraper(), arg);
    }

    @Test
    void parseFrenchDate_validFrenchDate_parsesCorrectly() throws Exception {
        Object result = invoke("parseFrenchDate", String.class, "Date de Parution : 1 juin 2026");

        assertThat(result).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay());
    }

    @Test
    void parseFrenchDate_accentedMonthAout_parsesCorrectly() throws Exception {
        Object result = invoke("parseFrenchDate", String.class, "Publié le 15 août 2026");

        assertThat(result).isEqualTo(LocalDate.of(2026, 8, 15).atStartOfDay());
    }

    @Test
    void parseFrenchDate_unknownMonthName_returnsNull() throws Exception {
        Object result = invoke("parseFrenchDate", String.class, "10 nimporte 2026");

        assertThat(result).isNull();
    }

    @Test
    void parseFrenchDate_noDatePattern_returnsNull() throws Exception {
        Object result = invoke("parseFrenchDate", String.class, "Aucune date ici");

        assertThat(result).isNull();
    }

    @Test
    void cleanLogo_placeholderImage_returnsNull() throws Exception {
        Object result = invoke("cleanLogo", String.class, "https://site/img/pasdimage.png");

        assertThat(result).isNull();
    }

    @Test
    void cleanLogo_realLogoUrl_isReturnedUnchanged() throws Exception {
        Object result = invoke("cleanLogo", String.class, "https://site/logos/acme.png");

        assertThat(result).isEqualTo("https://site/logos/acme.png");
    }

    @Test
    void cleanLogo_blankSrc_returnsNull() throws Exception {
        assertThat(invoke("cleanLogo", String.class, "")).isNull();
    }
}
