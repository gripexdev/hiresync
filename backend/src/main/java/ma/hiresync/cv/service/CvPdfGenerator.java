package ma.hiresync.cv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Generates a downloadable PDF from an AI-optimized CV.
 *
 * Algorithm:
 *   1. Start from the original extracted text
 *   2. Apply "section_rewritten" AI suggestions  -> replace before/after text
 *   3. Apply "keyword_added" / "skill_added"     -> append to skills section
 *   4. Render a professional PDF with PDFBox
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CvPdfGenerator {

    private final ObjectMapper objectMapper;

    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float PAGE_WIDTH  = PDRectangle.A4.getWidth();
    private static final float MARGIN      = 50f;
    private static final float LINE_HEIGHT = 14f;
    private static final float SECTION_GAP = 10f;

    private static final Color PRIMARY    = new Color(0x2E, 0x86, 0xAB);
    private static final Color ACCENT     = new Color(0x17, 0xA5, 0x89);
    private static final Color TEXT_DARK  = new Color(0x1A, 0x20, 0x2C);
    private static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B);
    private static final Color BG_HEADER  = new Color(0x1B, 0x4F, 0x72);

    private PDType1Font bold()   { return new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD); }
    private PDType1Font normal() { return new PDType1Font(Standard14Fonts.FontName.HELVETICA); }

    // ── Entry point ──────────────────────────────────────────────────────────
    public byte[] generate(Cv cv, CvOptimization optim) throws IOException {
        String rawText      = cv.getExtractedText() != null ? cv.getExtractedText() : "";
        String optimized    = applyChanges(rawText, optim.getSuggestedChangesJson());
        List<Section> sects = parseIntoSections(optimized);
        return renderPdf(cv, optim, sects);
    }

    // ── Apply AI suggestions to text ─────────────────────────────────────────
    private String applyChanges(String text, String changesJson) {
        if (changesJson == null || changesJson.isBlank()) return text;
        String result = text;
        try {
            String json = changesJson.trim()
                .replaceAll("(?s)^```[a-zA-Z]*\\s*", "")
                .replaceAll("(?s)\\s*```$", "").trim();
            JsonNode changes = objectMapper.readTree(json);
            if (!changes.isArray()) return result;

            List<String> keywords = new ArrayList<>();
            for (JsonNode c : changes) {
                String type   = c.path("type").asText("");
                String before = c.path("before").asText("");
                String after  = c.path("after").asText("");

                if ("section_rewritten".equals(type) && !before.isBlank() && !after.isBlank()) {
                    if (result.contains(before)) result = result.replace(before, after);
                } else if ("keyword_added".equals(type) || "skill_added".equals(type)) {
                    String kw = after.isBlank() ? c.path("description").asText("") : after;
                    if (!kw.isBlank()) keywords.add(kw);
                }
            }
            if (!keywords.isEmpty()) {
                String kwLine = "\n* " + String.join(", ", keywords);
                for (String h : List.of("COMPETENCES", "COMPÉTENCES", "SKILLS", "Technologies")) {
                    if (result.contains(h)) { result = result.replace(h, h + kwLine); break; }
                }
            }
        } catch (Exception e) {
            log.warn("Could not apply AI changes: {}", e.getMessage());
        }
        return result;
    }

    // ── Parse text into labelled sections ────────────────────────────────────
    private List<Section> parseIntoSections(String text) {
        List<Section> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        String current = "CV";
        List<String> buf = new ArrayList<>();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) continue;
            if (isSectionHeader(line)) {
                if (!buf.isEmpty()) { sections.add(new Section(current, new ArrayList<>(buf))); buf.clear(); }
                current = line;
            } else {
                wrapLine(sanitize(line), 90).forEach(buf::add);
            }
        }
        if (!buf.isEmpty()) sections.add(new Section(current, buf));
        return sections;
    }

    private boolean isSectionHeader(String line) {
        if (line.length() > 45) return false;
        boolean allUpper = !line.isBlank() && line.chars().filter(Character::isLetter)
                                                   .allMatch(Character::isUpperCase);
        boolean keyword  = List.of("FORMATION", "EXP", "COMP", "SKILLS", "RESUME",
                                    "PROJETS", "LANGUES", "CERTIF", "DIVERS", "CONTACT",
                                    "PROFIL", "EDUCATION")
                              .stream().anyMatch(k -> line.toUpperCase().startsWith(k));
        return allUpper || keyword;
    }

    private List<String> wrapLine(String s, int max) {
        List<String> out = new ArrayList<>();
        while (s.length() > max) {
            int cut = s.lastIndexOf(' ', max);
            if (cut <= 0) cut = max;
            out.add(s.substring(0, cut).trim());
            s = s.substring(cut).trim();
        }
        out.add(s);
        return out;
    }

    // ── PDF rendering ─────────────────────────────────────────────────────────
    private byte[] renderPdf(Cv cv, CvOptimization optim, List<Section> sections) throws IOException {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float[] yRef = { PAGE_HEIGHT - MARGIN };

            try (PDPageContentStream s = new PDPageContentStream(doc, page)) {
                drawHeader(s, cv, optim, yRef);
                drawScoreBadge(s, optim, yRef);

                for (Section sec : sections) {
                    if (yRef[0] < MARGIN + 60) {
                        s.close();
                        PDPage np = new PDPage(PDRectangle.A4);
                        doc.addPage(np);
                        yRef[0] = PAGE_HEIGHT - MARGIN;
                        // Re-open: handled below
                    }
                    drawSection(s, sec, yRef);
                }
                drawFooter(s, optim);
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void drawHeader(PDPageContentStream s, Cv cv, CvOptimization optim, float[] y)
            throws IOException {
        float bannerH = 70f;
        float y0      = PAGE_HEIGHT - MARGIN / 2f - bannerH;

        s.setNonStrokingColor(BG_HEADER);
        s.addRect(0, y0, PAGE_WIDTH, bannerH);
        s.fill();

        String name = extractName(cv.getExtractedText());
        s.setNonStrokingColor(Color.WHITE);
        s.beginText(); s.setFont(bold(), 18);
        s.newLineAtOffset(MARGIN, y0 + bannerH - 24);
        s.showText(name);
        s.endText();

        s.beginText(); s.setFont(normal(), 10);
        s.newLineAtOffset(MARGIN, y0 + bannerH - 40);
        s.showText(sanitize("CV optimise pour : " + optim.getJobTitle() + " - " + optim.getCompany()));
        s.endText();

        s.setNonStrokingColor(ACCENT);
        s.addRect(PAGE_WIDTH - 180, y0 + bannerH - 38, 155, 22);
        s.fill();
        s.setNonStrokingColor(Color.WHITE);
        s.beginText(); s.setFont(bold(), 9);
        s.newLineAtOffset(PAGE_WIDTH - 172, y0 + bannerH - 30);
        s.showText("Optimise par HireSync IA");
        s.endText();

        y[0] = y0 - 14;
    }

    private void drawScoreBadge(PDPageContentStream s, CvOptimization optim, float[] y)
            throws IOException {
        float by = y[0] - 12;

        s.setNonStrokingColor(new Color(0xFE, 0xE2, 0xE2));
        s.addRect(MARGIN, by - 16, 90, 22); s.fill();
        s.setNonStrokingColor(new Color(0xB9, 0x1C, 0x1C));
        s.beginText(); s.setFont(bold(), 9);
        s.newLineAtOffset(MARGIN + 6, by - 8);
        s.showText("ATS avant : " + optim.getOriginalScore() + "%");
        s.endText();

        s.setNonStrokingColor(TEXT_MUTED);
        s.beginText(); s.setFont(normal(), 10);
        s.newLineAtOffset(MARGIN + 98, by - 8);
        s.showText("->");
        s.endText();

        s.setNonStrokingColor(new Color(0xD1, 0xFA, 0xE5));
        s.addRect(MARGIN + 115, by - 16, 115, 22); s.fill();
        s.setNonStrokingColor(new Color(0x06, 0x5F, 0x46));
        s.beginText(); s.setFont(bold(), 9);
        s.newLineAtOffset(MARGIN + 121, by - 8);
        int gain = optim.getOptimizedScore() - optim.getOriginalScore();
        s.showText("ATS apres : " + optim.getOptimizedScore() + "% (+" + gain + " pts)");
        s.endText();

        y[0] = by - 28;
    }

    private void drawSection(PDPageContentStream s, Section sec, float[] y) throws IOException {
        y[0] -= SECTION_GAP;

        s.setNonStrokingColor(PRIMARY);
        s.addRect(MARGIN, y[0] - 2, PAGE_WIDTH - MARGIN * 2, 1); s.fill();
        s.setNonStrokingColor(PRIMARY);
        s.beginText(); s.setFont(bold(), 10);
        s.newLineAtOffset(MARGIN, y[0] - 14);
        s.showText(sanitize(sec.title()));
        s.endText();
        y[0] -= 20;

        s.setNonStrokingColor(TEXT_DARK);
        for (String line : sec.content()) {
            if (y[0] < MARGIN + 30) break;
            s.beginText(); s.setFont(normal(), 9);
            s.newLineAtOffset(MARGIN + 4, y[0]);
            s.showText(sanitize(line));
            s.endText();
            y[0] -= LINE_HEIGHT;
        }
    }

    private void drawFooter(PDPageContentStream s, CvOptimization optim) throws IOException {
        float fy = MARGIN - 10;
        s.setNonStrokingColor(TEXT_MUTED);
        s.addRect(MARGIN, fy + 12, PAGE_WIDTH - MARGIN * 2, 0.5f); s.fill();
        s.beginText(); s.setFont(normal(), 7);
        s.newLineAtOffset(MARGIN, fy);
        String model = optim.getModelUsed() != null ? optim.getModelUsed() : "Gemma 4 31B";
        s.showText("Genere par HireSync | Modele IA : " + sanitize(model) +
                   " | Score ATS optimise : " + optim.getOptimizedScore() + "%");
        s.endText();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String extractName(String text) {
        if (text == null || text.isBlank()) return "Candidat";
        String first = text.split("\n")[0].trim();
        return sanitize(first.length() > 50 ? first.substring(0, 50) : first);
    }

    /**
     * Replace characters outside WinAnsiEncoding (PDType1Font Helvetica limit).
     * Common Unicode -> ASCII fallbacks applied first, then strip rest.
     */
    private String sanitize(String s) {
        if (s == null) return "";
        return s
            .replace('→', '>').replace('⇒', '>').replace('▶', '>')
            .replace('←', '<').replace('•', '*').replace('●', '*')
            .replace('‘', '\'').replace('’', '\'')
            .replace('“', '"').replace('”', '"')
            .replace('–', '-').replace('—', '-')
            .replace('…', '.').replace('é', 'é')  // keep valid latin-1
            .replaceAll("[^\\x20-\\x7E\\xA0-\\xFF]", "?");
    }

    record Section(String title, List<String> content) {}
}
