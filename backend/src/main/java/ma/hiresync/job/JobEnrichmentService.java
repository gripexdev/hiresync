package ma.hiresync.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 *   - description  → "Poste :" section + "Profil recherché :" section
 *                     paragraphs preserved with \n\n, <br> items with \n
 *   - requirements → span.tagSkills items (skills / personality traits)
 *
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
                .referrer("https://www.rekrute.com")
                .timeout(12_000)
                .get();

        // ── Description: merge "Poste :" and "Profil recherché :" sections ──
        String poste  = extractSection(doc, "Poste");
        String profil = extractSection(doc, "Profil");

        String description = null;
        if (poste != null && profil != null) {
            description = poste + "\n\nProfil recherché :\n" + profil;
        } else if (poste != null) {
            description = poste;
        } else if (profil != null) {
            description = profil;
        }

        // ── Requirements: span.tagSkills (personality traits + skills) ───────
        List<String> requirements = doc.select("span.tagSkills").stream()
                .map(s -> s.ownText().trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // ── Persist ───────────────────────────────────────────────────────────
        if (description != null) job.setDescription(description);
        if (!requirements.isEmpty()) job.setRequirements(requirements);
        job.setEnriched(true);
        jobRepository.save(job);

        log.info("  → {} chars description, {} requirements",
                description != null ? description.length() : 0,
                requirements.size());
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
