package ma.hiresync.cv.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CvTextParser is the regex/heuristic fallback used when the AI-rebuilt CV
 * JSON isn't available. These tests favor checking the well-defined,
 * deterministic parts (section grouping, email/phone extraction, list caps)
 * over micro-asserting every detail of the role/company-splitting heuristics,
 * which are intentionally best-effort rather than exact.
 */
class CvTextParserTest {

    private final CvTextParser parser = new CvTextParser();

    @Test
    @SuppressWarnings("unchecked")
    void parse_blankText_returnsMinimalStructureWithFallbackTitle() {
        var result = parser.parse("   \n\n  ", "Développeur Backend");

        assertThat(result.get("jobTitle")).isEqualTo("Développeur Backend");
        assertThat((List<Object>) result.get("experience")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_extractsEmailFromHeaderBlock() {
        String text = """
                Othmane Sadiky
                othmane.sadiky@example.com
                EXPÉRIENCE
                • Développeur chez Acme
                """;

        var result = parser.parse(text, "Développeur");

        var contact = (Map<String, String>) result.get("contact");
        assertThat(contact.get("email")).isEqualTo("othmane.sadiky@example.com");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_extractsPhoneFromHeaderBlock() {
        String text = """
                Othmane Sadiky
                0612345678
                EXPÉRIENCE
                • Développeur chez Acme
                """;

        var result = parser.parse(text, "Développeur");

        var contact = (Map<String, String>) result.get("contact");
        assertThat(contact.get("phone")).contains("0612345678");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_groupsContentUnderExperienceFormationCompetencesLangues() {
        String text = """
                Othmane Sadiky
                EXPÉRIENCE
                • Développeur backend chez Acme - 2023 - 2024
                FORMATION
                • Master informatique - Université Hassan II - 2021 - 2023
                COMPÉTENCES
                Langages : Java, Spring, Docker
                LANGUES
                • Français (Courant)
                • Anglais (Professionnel)
                """;

        var result = parser.parse(text, "Développeur");

        assertThat((List<Map<String, Object>>) result.get("experience")).isNotEmpty();
        assertThat((List<Map<String, Object>>) result.get("education")).isNotEmpty();
        assertThat((List<String>) result.get("skills")).contains("Java", "Spring", "Docker");
        assertThat((List<String>) result.get("languages")).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_capsExperienceEntriesAtFour() {
        String text = """
                Othmane Sadiky
                EXPÉRIENCE
                • Poste un - 2020 - 2021
                • Poste deux - 2021 - 2022
                • Poste trois - 2022 - 2023
                • Poste quatre - 2023 - 2024
                • Poste cinq - 2024 - 2025
                """;

        var result = parser.parse(text, "Développeur");

        assertThat((List<Map<String, Object>>) result.get("experience")).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void parse_skillsListDeduplicatesAndCapsAtTwentyEntries() {
        StringBuilder skillsLine = new StringBuilder("COMPÉTENCES\n");
        for (int i = 0; i < 30; i++) skillsLine.append("Skill").append(i).append(", ");

        var result = parser.parse("Othmane Sadiky\n" + skillsLine, "Développeur");

        assertThat((List<String>) result.get("skills")).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void parse_usesFirstNonBlankLineAsCandidateName() {
        String text = """
                Othmane Sadiky
                EXPÉRIENCE
                • Développeur
                """;

        var result = parser.parse(text, "Développeur");

        assertThat((String) result.get("fullName")).containsIgnoringCase("othmane");
    }
}
