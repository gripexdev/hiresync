package ma.hiresync.cv.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AtsScorer is pure, deterministic logic (no Spring context, no I/O), which
 * makes it the cheapest and most valuable unit to cover with fine-grained tests.
 */
class AtsScorerTest {

    private final AtsScorer scorer = new AtsScorer();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void computeScore_blankOrNullText_returnsFloorScore(String text) {
        assertThat(scorer.computeScore(text)).isEqualTo(10);
    }

    @Test
    void computeScore_richCv_scoresHigherThanThinCv() {
        String richCv = """
                Résumé
                Ingénieur logiciel avec expérience en Java, Spring, Angular, Docker et CI/CD.

                Expérience
                Développé une architecture microservices, géré une équipe de 3 développeurs,
                conçu et optimisé des API REST. Réduit le temps de réponse de 40%.

                Formation
                Diplôme d'ingénieur en informatique.

                Compétences
                Java, Spring, Angular, PostgreSQL, Docker, Kubernetes, Git, AWS, Scrum

                Contact
                email: candidat@example.com  phone: 0612345678
                """;
        String thinCv = "Bonjour je cherche un emploi.";

        int richScore = scorer.computeScore(richCv);
        int thinScore = scorer.computeScore(thinCv);

        assertThat(richScore).isGreaterThan(thinScore);
        assertThat(richScore).isLessThanOrEqualTo(100);
    }

    @Test
    void computeScore_neverExceedsOneHundred() {
        // Pile on every keyword category at once to try to overflow the cap.
        String kitchenSink = """
                experience expérience education formation skills compétences summary résumé
                objective contact email phone téléphone
                developed développé managed géré led dirigé created créé implemented implémenté
                java spring angular react python sql postgresql docker kubernetes git rest api
                microservices agile scrum ci/cd aws azure
                email@test.com 0612345678
                """.repeat(5);

        assertThat(scorer.computeScore(kitchenSink)).isLessThanOrEqualTo(100);
    }

    @Test
    void computeJobMatch_allKeywordsPresent_scoresFullKeywordWeight() {
        String cvText = "Expérience en Java, Spring Boot et Docker. Formation ingénieur. "
                + "Compétences: Java, Spring, Docker. Contact: a@b.com 0612345678. "
                + "Géré une équipe et réduit les coûts de 20%.";
        List<String> keywords = List.of("Java", "Spring", "Docker");

        AtsScorer.JobMatch result = scorer.computeJobMatch(cvText, keywords);

        assertThat(result.matched()).containsExactlyInAnyOrder("Java", "Spring", "Docker");
        assertThat(result.missing()).isEmpty();
        assertThat(result.keywordMatchPct()).isEqualTo(100);
        assertThat(result.score()).isGreaterThan(80);
    }

    @Test
    void computeJobMatch_noKeywordsMatch_reportsAllAsMissing() {
        String cvText = "Vendeur en boulangerie depuis cinq ans.";
        List<String> keywords = List.of("Kubernetes", "Terraform");

        AtsScorer.JobMatch result = scorer.computeJobMatch(cvText, keywords);

        assertThat(result.matched()).isEmpty();
        assertThat(result.missing()).containsExactlyInAnyOrder("Kubernetes", "Terraform");
        assertThat(result.keywordMatchPct()).isZero();
    }

    @Test
    void computeJobMatch_shortTokenMatchesAsSubstring() {
        // "SQL" is <= 3 chars: matched via plain substring, not word-boundary regex.
        AtsScorer.JobMatch result = scorer.computeJobMatch("Maîtrise du SQL avancé", List.of("SQL"));
        assertThat(result.matched()).containsExactly("SQL");
    }

    @Test
    void computeJobMatch_singleWordKeyword_doesNotMatchAsSubstringOfLongerWord() {
        // "java" must not match inside "javascript" — word-boundary regex guards this.
        AtsScorer.JobMatch result = scorer.computeJobMatch("Expert en JavaScript moderne", List.of("java"));
        assertThat(result.matched()).isEmpty();
        assertThat(result.missing()).containsExactly("java");
    }

    @Test
    void computeJobMatch_nullKeywordList_doesNotThrow() {
        AtsScorer.JobMatch result = scorer.computeJobMatch("Un CV quelconque", null);
        assertThat(result.matched()).isEmpty();
        assertThat(result.missing()).isEmpty();
    }

    @Test
    void parseSections_groupsContentUnderDetectedHeaders() {
        String text = """
                EXPÉRIENCE
                Développeur chez Acme depuis 2022.

                FORMATION
                Master en informatique.
                """;

        var sections = scorer.parseSections(text);

        assertThat(sections).containsKey("EXPÉRIENCE");
        assertThat(sections.get("EXPÉRIENCE")).contains("Acme");
        assertThat(sections).containsKey("FORMATION");
        assertThat(sections.get("FORMATION")).contains("Master");
    }

    @Test
    void parseSections_blankText_returnsEmptyMap() {
        assertThat(scorer.parseSections(null)).isEmpty();
        assertThat(scorer.parseSections("   ")).isEmpty();
    }
}
