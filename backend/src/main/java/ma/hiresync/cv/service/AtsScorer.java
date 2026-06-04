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
