package ma.hiresync.cv.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

/**
 * Extracts text from a PDF and computes an initial ATS score.
 *
 * Algorithm:
 *   - Extract all text via Apache PDFBox
 *   - Check presence of critical CV sections (contact, summary, experience, education, skills)
 *   - Score readability markers (dates, action verbs, quantified achievements)
 *   - Result: 0–100 integer score
 */
@Component
@Slf4j
public class AtsScorer {

    // ATS sections (must-haves)
    private static final List<String> SECTION_KEYWORDS = List.of(
            "experience", "expérience", "education", "formation", "skills", "compétences",
            "summary", "résumé", "objective", "contact", "email", "phone", "téléphone"
    );

    // Action verbs that ATS systems value
    private static final List<String> ACTION_VERBS = List.of(
            "developed", "développé", "managed", "géré", "led", "dirigé",
            "created", "créé", "implemented", "implémenté", "designed", "conçu",
            "built", "construit", "optimized", "optimisé", "delivered", "livré",
            "achieved", "atteint", "improved", "amélioré", "reduced", "réduit"
    );

    // Tech keywords commonly scanned by ATS
    private static final List<String> TECH_KEYWORDS = List.of(
            "java", "spring", "angular", "react", "python", "sql", "postgresql",
            "docker", "kubernetes", "git", "rest", "api", "microservices",
            "agile", "scrum", "ci/cd", "aws", "azure"
    );

    /**
     * Extract text from an uploaded PDF file using Apache PDFBox.
     */
    public String extractText(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            var stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (Exception e) {
            log.warn("PDFBox extraction failed for {}: {}", file.getOriginalFilename(), e.getMessage());
            return "";
        }
    }

    /**
     * Compute ATS score (0–100) from extracted CV text.
     *
     * Breakdown:
     *   - Sections present     → 30 pts  (6 pts each, 5 sections)
     *   - Action verbs count   → 20 pts  (2 pts each, max 10)
     *   - Tech keywords count  → 30 pts  (3 pts each, max 10)
     *   - Length adequacy      → 10 pts  (300–1500 words is ideal)
     *   - Contact info         → 10 pts  (email + phone)
     */
    public int computeScore(String text) {
        if (text == null || text.isBlank()) return 10;
        String lower = text.toLowerCase();
        int score = 0;

        // 1. Sections (30 pts)
        long sections = SECTION_KEYWORDS.stream().filter(lower::contains).count();
        score += Math.min(sections * 5, 30);

        // 2. Action verbs (20 pts)
        long verbs = ACTION_VERBS.stream().filter(lower::contains).count();
        score += Math.min(verbs * 2, 20);

        // 3. Tech keywords (30 pts)
        long tech = TECH_KEYWORDS.stream().filter(lower::contains).count();
        score += Math.min(tech * 3, 30);

        // 4. Length (10 pts)
        int wordCount = text.split("\\s+").length;
        if (wordCount >= 300 && wordCount <= 1500) score += 10;
        else if (wordCount > 100)                  score +=  5;

        // 5. Contact info (10 pts)
        boolean hasEmail = lower.contains("@") || lower.contains("email");
        boolean hasPhone = text.matches(".*\\d{8,}.*") || lower.contains("phone") || lower.contains("tel");
        if (hasEmail) score += 5;
        if (hasPhone) score += 5;

        return Math.min(score, 100);
    }

    /**
     * Job-specific ATS match result: an explainable, deterministic score of how
     * well a CV matches ONE job, plus which of the job's keywords are present/absent.
     */
    public record JobMatch(
            int score,                 // 0–100, weighted ATS-style score for this job
            int keywordMatchPct,       // % of job keywords found in the CV
            List<String> matched,      // job keywords present in the CV
            List<String> missing       // job keywords absent from the CV
    ) {}

    /**
     * Compute a job-specific ATS match score for {@code cvText} against the
     * keywords extracted from a job description. Deterministic and explainable —
     * mirrors how real ATS systems weight keyword relevance most heavily.
     *
     * Weighting (research-based, see README §ATS):
     *   - Keyword match     → 55 pts  (dominant factor, 40–50% in real ATS)
     *   - Section presence  → 20 pts  (summary, experience, education, skills)
     *   - Contact info      → 10 pts  (email + phone in body)
     *   - Impact markers    → 15 pts  (action verbs + quantified achievements)
     */
    public JobMatch computeJobMatch(String cvText, List<String> jdKeywords) {
        String lower = cvText == null ? "" : cvText.toLowerCase();

        // ── Keyword match (55 pts) ──────────────────────────────────────────
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        if (jdKeywords != null) {
            for (String kw : jdKeywords) {
                if (kw == null || kw.isBlank()) continue;
                if (containsKeyword(lower, kw.toLowerCase().trim())) matched.add(kw.trim());
                else missing.add(kw.trim());
            }
        }
        int total = matched.size() + missing.size();
        int keywordPct = total == 0 ? 0 : (int) Math.round(100.0 * matched.size() / total);
        double keywordPts = total == 0 ? 30 : 55.0 * matched.size() / total;

        // ── Section presence (20 pts: 5 each for summary, experience, education, skills) ─
        int sectionPts = 0;
        if (containsAny(lower, "résumé", "resume", "summary", "profil", "objectif", "à propos")) sectionPts += 5;
        if (containsAny(lower, "expérience", "experience", "parcours"))                          sectionPts += 5;
        if (containsAny(lower, "formation", "education", "diplôme", "études"))                    sectionPts += 5;
        if (containsAny(lower, "compétences", "skills", "competences"))                          sectionPts += 5;

        // ── Contact info (10 pts) ──────────────────────────────────────────
        int contactPts = 0;
        if (lower.contains("@")) contactPts += 5;
        if (lower.matches("(?s).*\\d{6,}.*") || containsAny(lower, "tél", "tel", "phone")) contactPts += 5;

        // ── Impact markers (15 pts: action verbs + quantified achievements) ─
        long verbs = ACTION_VERBS.stream().filter(lower::contains).count();
        int verbPts = (int) Math.min(verbs * 2, 8);
        boolean quantified = cvText != null && cvText.matches("(?s).*\\d+\\s?%.*")  // percentages
                || lower.matches("(?s).*\\d{2,}.*");                                 // any sizable number
        int impactPts = verbPts + (quantified ? 7 : 0);

        int score = (int) Math.round(Math.min(100, keywordPts + sectionPts + contactPts + impactPts));
        return new JobMatch(score, keywordPct, matched, missing);
    }

    /** Case-insensitive keyword presence. Multi-word keywords match as a substring;
     *  single tokens match on word-ish boundaries so "java" doesn't match "javascript". */
    private boolean containsKeyword(String haystackLower, String keywordLower) {
        if (keywordLower.isBlank()) return false;
        if (keywordLower.contains(" ") || keywordLower.length() <= 3) {
            // phrases & short tokens (C#, SQL, AWS): plain substring is reliable enough
            return haystackLower.contains(keywordLower);
        }
        // single word: require a non-letter boundary to avoid "java" ⊂ "javascript"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?<![a-zà-ÿ])" + java.util.regex.Pattern.quote(keywordLower) + "(?![a-zà-ÿ])")
                .matcher(haystackLower);
        return m.find();
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    /**
     * Parse the CV text into labelled sections for display in the frontend.
     */
    public Map<String, String> parseSections(String text) {
        var map = new LinkedHashMap<String, String>();
        if (text == null || text.isBlank()) return map;

        String[] lines = text.split("\n");
        String currentSection = "Contenu";
        var buffer = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;

            String low = trimmed.toLowerCase();
            if (looksLikeHeader(low)) {
                if (!buffer.isEmpty()) map.put(currentSection, buffer.toString().trim());
                currentSection = trimmed;
                buffer = new StringBuilder();
            } else {
                buffer.append(trimmed).append(" ");
            }
        }
        if (!buffer.isEmpty()) map.put(currentSection, buffer.toString().trim());
        return map;
    }

    private boolean looksLikeHeader(String line) {
        return (line.length() < 40)
            && (SECTION_KEYWORDS.stream().anyMatch(line::contains)
                || line.matches("[A-ZÁÀÂÉÈÊÙÛÎÏÔŒÇ\\s]{4,}"));
    }
}
