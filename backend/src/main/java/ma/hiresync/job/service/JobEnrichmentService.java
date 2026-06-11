package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches each job's detail page (sourceUrl) and fills in:
 *   - description  → "Poste :" section + "Profil recherché :" section,
 *                     paragraphs preserved with \n\n, <br> / <li> items with \n
 *   - requirements → skills / personality traits listed on the detail page
 *
 * Extraction is source-specific (rekrute.com vs emploi.ma — see job.getSource()).
 * Called via POST /api/admin/enrich/trigger — processes up to 20 jobs per call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobEnrichmentService {

    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)";

    private final JobRepository jobRepository;

    // ── Public entry point ────────────────────────────────────────────────────

    public int enrich() {
        List<Job> jobs = jobRepository.findTop20ByEnrichedFalseOrderByScrapedAtDesc();
        if (jobs.isEmpty()) {
            log.info("Enrichment: nothing to do — all jobs already enriched.");
            return 0;
        }

        int count = 0;
        for (Job job : jobs) {
            try {
                enrichOne(job);
                count++;
            } catch (Exception e) {
                log.warn("Enrichment failed for job {} ({}): {}",
                        job.getId(), job.getSourceUrl(), e.getMessage());
                // Mark enriched anyway so we don't retry a permanently broken URL
                job.setEnriched(true);
                jobRepository.save(job);
            }

            try { Thread.sleep(400); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt(); break;
            }
        }

        log.info("Enrichment pass complete — {} jobs enriched", count);
        return count;
    }

    // ── Per-job detail fetch ──────────────────────────────────────────────────

    private void enrichOne(Job job) throws Exception {
        log.info("Enriching job {} → {}", job.getId(), job.getSourceUrl());

        Document doc = Jsoup.connect(job.getSourceUrl())
                .userAgent(USER_AGENT)
                .referrer("https://www." + job.getSource())
                .timeout(12_000)
                .get();

        String description;
        List<String> requirements;
        if ("emploi.ma".equals(job.getSource())) {
            description  = extractEmploiMaDescription(doc);
            requirements = extractEmploiMaSkills(doc);
        } else if ("indeed.ma".equals(job.getSource())) {
            description  = extractIndeedDescription(doc);
            requirements = List.of();
        } else {
            description  = extractRekruteDescription(doc);
            requirements = extractRekruteSkills(doc);
        }

        // ── Persist ───────────────────────────────────────────────────────────
        if (description != null) job.setDescription(description);
        if (!requirements.isEmpty()) job.setRequirements(requirements);
        job.setEnriched(true);
        jobRepository.save(job);

        log.info("  → {} chars description, {} requirements",
                description != null ? description.length() : 0,
                requirements.size());
    }

    // ── rekrute.com extraction ───────────────────────────────────────────────

    /** Merges the "Poste :" and "Profil recherché :" sections (each a {@code div.blc}). */
    private String extractRekruteDescription(Document doc) {
        String poste  = extractSection(doc, "Poste");
        String profil = extractSection(doc, "Profil");

        if (poste != null && profil != null) {
            return poste + "\n\nProfil recherché :\n" + profil;
        }
        return poste != null ? poste : profil;
    }

    /** Personality traits + skills, rendered as {@code span.tagSkills}. */
    private List<String> extractRekruteSkills(Document doc) {
        return doc.select("span.tagSkills").stream()
                .map(s -> s.ownText().trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    // ── emploi.ma extraction ─────────────────────────────────────────────────

    /** Merges {@code div.job-description} ("Poste proposé") and {@code div.job-qualifications} ("Profil recherché"). */
    private String extractEmploiMaDescription(Document doc) {
        String poste  = divToText(doc.selectFirst("div.job-description"));
        String profil = divToText(doc.selectFirst("div.job-qualifications"));

        if (poste != null && profil != null) {
            return poste + "\n\nProfil recherché :\n" + profil;
        }
        return poste != null ? poste : profil;
    }

    /** Skills listed as {@code ul.skills li}. */
    private List<String> extractEmploiMaSkills(Document doc) {
        return doc.select("ul.skills li").stream()
                .map(Element::text)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    // ── indeed.ma extraction ──────────────────────────────────────────────────

    /** Job description lives in {@code div#jobDescriptionText} ({@code <p>} and {@code <ul><li>} children). */
    private String extractIndeedDescription(Document doc) {
        return divToText(doc.selectFirst("div#jobDescriptionText"));
    }

    /**
     * Converts a content {@code <div>} (made of {@code <p>} and {@code <ul><li>} children)
     * to plain text — paragraphs joined with \n\n, list items prefixed with "- ".
     */
    private String divToText(Element div) {
        if (div == null) return null;
        StringBuilder sb = new StringBuilder();
        for (Element child : div.children()) {
            if (child.tagName().equals("ul")) {
                for (Element li : child.select("li")) {
                    String text = li.text().trim();
                    if (!text.isEmpty()) sb.append("- ").append(text).append("\n");
                }
                sb.append("\n");
            } else {
                String text = nodeToText(child).trim()
                        .replaceAll("[ \\t]+", " ")
                        .replaceAll(" \\n", "\n")
                        .replaceAll("\\n ", "\n")
                        .replaceAll("\\n{3,}", "\n\n");
                if (!text.isEmpty()) sb.append(text).append("\n\n");
            }
        }
        String result = sb.toString().replaceAll("\\n{3,}", "\n\n").trim();
        return result.isEmpty() ? null : result;
    }

    // ── HTML → structured text helpers ───────────────────────────────────────

    /**
     * Finds a {@code div.blc} whose {@code <h2>} text contains {@code headingKeyword},
     * then converts each child {@code <p>} to a paragraph (joined with \n\n).
     * Inside each paragraph, {@code <br>} tags become \n so bullet items stay separate.
     */
    private String extractSection(Document doc, String headingKeyword) {
        for (Element blc : doc.select("div.blc")) {
            Element h2 = blc.selectFirst("h2");
            if (h2 != null && h2.ownText().trim().contains(headingKeyword)) {
                StringBuilder sb = new StringBuilder();
                for (Element child : blc.children()) {
                    if (child.tagName().equals("h2")) continue;
                    String text = nodeToText(child).trim();
                    // Collapse multiple internal spaces but keep intentional newlines
                    text = text.replaceAll("[ \\t]+", " ")
                               .replaceAll(" \\n", "\n")
                               .replaceAll("\\n ", "\n")
                               .replaceAll("\\n{3,}", "\n\n")
                               .trim();
                    if (!text.isEmpty()) sb.append(text).append("\n\n");
                }
                String result = sb.toString()
                        .replaceAll("\\n{3,}", "\n\n")
                        .trim();
                return result.isEmpty() ? null : result;
            }
        }
        return null;
    }

    /**
     * Recursively converts an element to plain text, turning {@code <br>} into \n
     * and joining child element text without adding extra spaces.
     */
    private String nodeToText(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode) {
                sb.append(((TextNode) child).text());
            } else if (child instanceof Element el) {
                if (el.tagName().equalsIgnoreCase("br")) {
                    sb.append("\n");
                } else {
                    sb.append(nodeToText(el));
                }
            }
        }
        return sb.toString();
    }
}
