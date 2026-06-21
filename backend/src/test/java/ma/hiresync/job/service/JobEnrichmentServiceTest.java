package ma.hiresync.job.service;

import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * enrichOne() makes a real Jsoup HTTP call inline and isn't practical to unit
 * test, but every per-source extraction helper is a pure Document -> text/list
 * function, which we exercise directly via reflection with hand-built HTML
 * fixtures mirroring each source's real detail-page markup.
 */
@ExtendWith(MockitoExtension.class)
class JobEnrichmentServiceTest {

    @Mock private JobRepository jobRepository;

    private JobEnrichmentService service() {
        return new JobEnrichmentService(jobRepository);
    }

    private Object invoke(String method, String html) throws Exception {
        Document doc = Jsoup.parse(html);
        Method m = JobEnrichmentService.class.getDeclaredMethod(method, Document.class);
        m.setAccessible(true);
        return m.invoke(service(), doc);
    }

    @Test
    void enrich_noUnenrichedJobs_returnsZeroWithoutAnyNetworkCall() {
        when(jobRepository.findTop20ByEnrichedFalseOrderByScrapedAtDesc()).thenReturn(List.of());

        int count = service().enrich();

        assertThat(count).isZero();
        verify(jobRepository, never()).save(any());
    }

    @Test
    void extractRekruteDescription_mergesPosteAndProfilSections() throws Exception {
        String html = """
                <div class="blc"><h2>Poste :</h2><p>Développer des API REST.</p></div>
                <div class="blc"><h2>Profil recherché :</h2><p>3 ans d'expérience minimum.</p></div>
                """;

        Object result = invoke("extractRekruteDescription", html);

        assertThat(result).isEqualTo("Développer des API REST.\n\nProfil recherché :\n3 ans d'expérience minimum.");
    }

    @Test
    void extractRekruteSkills_readsTagSkillsSpans() throws Exception {
        String html = "<span class=\"tagSkills\">Rigueur</span><span class=\"tagSkills\">Autonomie</span>";

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) invoke("extractRekruteSkills", html);

        assertThat(result).containsExactly("Rigueur", "Autonomie");
    }

    @Test
    void extractEmploiMaDescription_mergesJobDescriptionAndQualifications() throws Exception {
        String html = """
                <div class="job-description"><p>Gérer les API.</p></div>
                <div class="job-qualifications"><p>Bac+5 en informatique.</p></div>
                """;

        Object result = invoke("extractEmploiMaDescription", html);

        assertThat(result).isEqualTo("Gérer les API.\n\nProfil recherché :\nBac+5 en informatique.");
    }

    @Test
    void extractEmploiMaSkills_readsSkillsList() throws Exception {
        String html = "<ul class=\"skills\"><li>Java</li><li>Spring</li></ul>";

        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) invoke("extractEmploiMaSkills", html);

        assertThat(result).containsExactly("Java", "Spring");
    }

    @Test
    void extractEmploiMaSector_findsSecteurLabelAndReturnsFieldValue() throws Exception {
        String html = """
                <div class="card-block-company">
                  <ul><li><strong>Secteur d'activité</strong><div class="field-item">Informatique</div></li></ul>
                </div>
                """;

        Object result = invoke("extractEmploiMaSector", html);

        assertThat(result).isEqualTo("Informatique");
    }

    @Test
    void extractEmploiMaSector_missingSecteurLabel_returnsNull() throws Exception {
        String html = "<div class=\"card-block-company\"><ul><li><strong>Autre</strong></li></ul></div>";

        Object result = invoke("extractEmploiMaSector", html);

        assertThat(result).isNull();
    }

    @Test
    void extractIndeedDescription_readsJobDescriptionTextDiv() throws Exception {
        String html = "<div id=\"jobDescriptionText\"><p>Mission principale.</p><ul><li>Java</li></ul></div>";

        Object result = invoke("extractIndeedDescription", html);

        assertThat(result).asString().contains("Mission principale.").contains("- Java");
    }

    @Test
    void extractLinkedInDescription_flattensInlineMarkupWithBreaksAndLists() throws Exception {
        String html = """
                <div class="show-more-less-html__markup">
                  Nous recherchons<br>un développeur.
                  <ul><li>Java</li><li>Spring</li></ul>
                </div>
                """;

        Object result = invoke("extractLinkedInDescription", html);

        assertThat(result).asString().contains("Nous recherchons").contains("un développeur").contains("- Java");
    }

    @Test
    void extractLinkedInDescription_noMarkupElement_returnsNull() throws Exception {
        Object result = invoke("extractLinkedInDescription", "<div>nothing relevant</div>");

        assertThat(result).isNull();
    }

    @Test
    void extractLinkedInCriteria_readsFourPositionalFieldsAndTranslatesArabic() throws Exception {
        String html = """
                <ul class="description__job-criteria-list">
                  <li class="description__job-criteria-item"><span class="description__job-criteria-text">مستوى المبتدئين</span></li>
                  <li class="description__job-criteria-item"><span class="description__job-criteria-text">دوام كامل</span></li>
                  <li class="description__job-criteria-item"><span class="description__job-criteria-text">تكنولوجيا المعلومات</span></li>
                  <li class="description__job-criteria-item"><span class="description__job-criteria-text">النفط والغاز</span></li>
                </ul>
                """;

        Method m = JobEnrichmentService.class.getDeclaredMethod("extractLinkedInCriteria", Document.class);
        m.setAccessible(true);
        Object criteria = m.invoke(service(), Jsoup.parse(html));

        // LinkedInCriteria is a private record — read its accessors via reflection too.
        Object experienceLevel = criteria.getClass().getMethod("experienceLevel").invoke(criteria);
        Object contractType = criteria.getClass().getMethod("contractType").invoke(criteria);
        Object sector = criteria.getClass().getMethod("sector").invoke(criteria);

        assertThat(experienceLevel).isEqualTo("Débutant");
        assertThat(contractType).isEqualTo("Temps plein");
        assertThat(sector).isEqualTo("Informatique / Pétrole et gaz");
    }

    @Test
    void extractLinkedInCriteria_fewerThanFourItems_leavesMissingFieldsNull() throws Exception {
        String html = """
                <ul class="description__job-criteria-list">
                  <li class="description__job-criteria-item"><span class="description__job-criteria-text">Full-time</span></li>
                </ul>
                """;

        Method m = JobEnrichmentService.class.getDeclaredMethod("extractLinkedInCriteria", Document.class);
        m.setAccessible(true);
        Object criteria = m.invoke(service(), Jsoup.parse(html));

        Object contractType = criteria.getClass().getMethod("contractType").invoke(criteria);
        assertThat(contractType).isNull();
    }
}
