package ma.hiresync.cv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the pure pre-processing logic (applying AI suggestions, splitting
 * text into sections, sanitizing for WinAnsiEncoding) via reflection, plus a
 * smoke test on the full generate() path — PDFBox renders entirely in-memory,
 * so unlike Playwright there's no external process to mock.
 */
class CvPdfGeneratorTest {

    private final CvPdfGenerator generator = new CvPdfGenerator(new ObjectMapper());

    private Object invoke(String method, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof String) types[i] = String.class;
            else if (args[i] instanceof Integer) types[i] = int.class; // unbox to match primitive params
            else types[i] = args[i].getClass();
        }
        Method m = CvPdfGenerator.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(generator, args);
    }

    @Test
    void generate_producesNonEmptyPdfBytesStartingWithPdfMagicHeader() throws Exception {
        Cv cv = Cv.builder().extractedText("RÉSUMÉ\nDéveloppeur backend.\n\nEXPÉRIENCE\nDeveloped APIs.").build();
        CvOptimization optim = CvOptimization.builder().jobTitle("Backend Dev").company("Acme")
                .originalScore(60).optimizedScore(85).modelUsed("Groq").build();

        byte[] pdf = generator.generate(cv, optim);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void applyChanges_sectionRewritten_replacesBeforeWithAfterInText() throws Exception {
        String text = "Développeur avec 2 ans d'expérience.";
        String changes = """
                [{"type":"section_rewritten","before":"Développeur avec 2 ans d'expérience.","after":"Ingénieur backend confirmé."}]
                """;

        Object result = invoke("applyChanges", text, changes);

        assertThat(result).isEqualTo("Ingénieur backend confirmé.");
    }

    @Test
    void applyChanges_keywordAdded_insertsAfterCompetencesHeading() throws Exception {
        String text = "COMPETENCES\nJava, Spring";
        String changes = """
                [{"type":"keyword_added","after":"Docker"},{"type":"skill_added","after":"Kubernetes"}]
                """;

        Object result = invoke("applyChanges", text, changes);

        assertThat(result).asString().contains("COMPETENCES\n* Docker, Kubernetes");
    }

    @Test
    void applyChanges_blankChangesJson_returnsTextUnchanged() throws Exception {
        Object result = invoke("applyChanges", "Texte original", "");
        assertThat(result).isEqualTo("Texte original");
    }

    @Test
    void applyChanges_malformedJson_failsGracefullyAndReturnsOriginalText() throws Exception {
        Object result = invoke("applyChanges", "Texte original", "not valid json {{{");
        assertThat(result).isEqualTo("Texte original");
    }

    @Test
    void isSectionHeader_allCapsShortLine_isRecognised() throws Exception {
        assertThat(invoke("isSectionHeader", "EXPÉRIENCE")).isEqualTo(true);
    }

    @Test
    void isSectionHeader_knownKeywordPrefixMixedCase_isRecognised() throws Exception {
        assertThat(invoke("isSectionHeader", "Formation académique")).isEqualTo(true);
    }

    @Test
    void isSectionHeader_longAllCapsLine_isNotAHeader() throws Exception {
        String longLine = "CECI EST UNE LIGNE BEAUCOUP TROP LONGUE POUR ETRE UN TITRE DE SECTION VALIDE";
        assertThat(invoke("isSectionHeader", longLine)).isEqualTo(false);
    }

    @Test
    void isSectionHeader_regularSentence_isNotAHeader() throws Exception {
        assertThat(invoke("isSectionHeader", "Développeur avec 3 ans d'expérience.")).isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void wrapLine_longLine_wrapsAtWordBoundaryUnderMaxLength() throws Exception {
        String longText = "Une phrase assez longue qui doit être coupée en plusieurs lignes pour tenir dans la largeur disponible";

        List<String> wrapped = (List<String>) invoke("wrapLine", longText, 30);

        assertThat(wrapped.size()).isGreaterThan(1);
        wrapped.forEach(line -> assertThat(line.length()).isLessThanOrEqualTo(30));
    }

    @Test
    void sanitize_replacesNonWinAnsiCharsWithAsciiFallbacks() throws Exception {
        Object result = invoke("sanitize", "Compétences → Java • Spring – Docker");

        assertThat(result).isEqualTo("Compétences > Java * Spring - Docker");
    }
}
