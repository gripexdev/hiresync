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
import org.jsoup.select.Elements;
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
                // (for indeed.ma, the snippet description from the listing stays
                // as a fallback when the detail page is rate-limited/blocked)
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

    // LinkedIn's bot-detection returns HTTP 999 for the generic HireSync-Bot UA on
    // /jobs/view/ pages — a real browser UA + Accept-Language is needed there.
    private static final String LINKEDIN_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private void enrichOne(Job job) throws Exception {
        log.info("Enriching job {} → {}", job.getId(), job.getSourceUrl());

        var connection = Jsoup.connect(job.getSourceUrl()).timeout(12_000);
        if ("linkedin.com".equals(job.getSource())) {
            connection.userAgent(LINKEDIN_USER_AGENT)
                       .header("Accept-Language", "fr-FR,fr;q=0.9");
        } else {
            connection.userAgent(USER_AGENT)
                       .referrer("https://www." + job.getSource());
        }
        Document doc = connection.get();

        String description;
        List<String> requirements;
        if ("emploi.ma".equals(job.getSource())) {
            description  = extractEmploiMaDescription(doc);
            requirements = extractEmploiMaSkills(doc);

            String sector = extractEmploiMaSector(doc);
            if (sector != null) job.setSector(sector);
        } else if ("indeed.ma".equals(job.getSource())) {
            description  = extractIndeedDescription(doc);
            requirements = List.of();
        } else if ("linkedin.com".equals(job.getSource())) {
            description  = extractLinkedInDescription(doc);
            requirements = List.of();

            LinkedInCriteria criteria = extractLinkedInCriteria(doc);
            if (criteria.experienceLevel() != null) job.setExperienceLevel(criteria.experienceLevel());
            if (criteria.contractType() != null)    job.setContractType(criteria.contractType());
            if (criteria.sector() != null)          job.setSector(criteria.sector());
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

    /**
     * The activity sector ("Secteur d'activité") isn't on the listing card — only
     * on the detail page, inside {@code div.card-block-company}'s {@code <li>} list.
     * Not every company profile fills it in, so a missing/empty value is tolerated
     * (the job simply keeps {@code sector = null}).
     */
    private String extractEmploiMaSector(Document doc) {
        for (Element li : doc.select("div.card-block-company li")) {
            Element strong = li.selectFirst("strong");
            if (strong == null || !strong.text().trim().startsWith("Secteur")) continue;
            Element field = li.selectFirst("div.field-item");
            if (field == null) return null;
            String text = field.text().trim();
            return text.isEmpty() ? null : text;
        }
        return null;
    }

    // ── indeed.ma extraction ──────────────────────────────────────────────────

    /** Job description lives in {@code div#jobDescriptionText} ({@code <p>} and {@code <ul><li>} children). */
    private String extractIndeedDescription(Document doc) {
        return divToText(doc.selectFirst("div#jobDescriptionText"));
    }

    // ── linkedin.com extraction ───────────────────────────────────────────────

    /**
     * Job description lives in {@code div.show-more-less-html__markup} — unlike the
     * other sources, its content is mostly inline ({@code <br>}, {@code <strong>}, text)
     * with the occasional {@code <ul><li>}, not wrapped in top-level {@code <p>} tags.
     */
    private String extractLinkedInDescription(Document doc) {
        Element markup = doc.selectFirst("div.show-more-less-html__markup");
        if (markup == null) return null;
        StringBuilder sb = new StringBuilder();
        htmlToText(markup, sb);
        String result = sb.toString()
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" \\n", "\n")
                .replaceAll("\\n ", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return result.isEmpty() ? null : result;
    }

    /** Seniority level, employment type and job function/industries — read from the "criteria" list. */
    private record LinkedInCriteria(String experienceLevel, String contractType, String sector) {}

    /**
     * The criteria list ({@code ul.description__job-criteria-list > li.description__job-criteria-item})
     * always renders the same 4 fields in the same order — Seniority level, Employment type,
     * Job function, Industries — but the {@code <h3>} labels are translated by LinkedIn into
     * whatever locale it served the page in (Arabic/French/English), so matching on label text
     * is unreliable. We read by position instead, and tolerate fewer than 4 items (or none)
     * by leaving the corresponding job fields untouched.
     */
    private LinkedInCriteria extractLinkedInCriteria(Document doc) {
        Elements items = doc.select("ul.description__job-criteria-list > li.description__job-criteria-item");

        String experienceLevel = toFrench(criteriaValue(items, 0));
        String contractType    = toFrench(criteriaValue(items, 1));
        String jobFunction     = toFrench(criteriaValue(items, 2));
        String industries      = toFrench(criteriaValue(items, 3));

        String sector;
        if (jobFunction != null && industries != null) {
            sector = jobFunction + " / " + industries;
        } else {
            sector = jobFunction != null ? jobFunction : industries;
        }

        return new LinkedInCriteria(experienceLevel, contractType, sector);
    }

    private String criteriaValue(Elements items, int index) {
        if (index >= items.size()) return null;
        Element span = items.get(index).selectFirst("span.description__job-criteria-text");
        if (span == null) return null;
        String text = span.text().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * LinkedIn's "criteria" values (seniority level, employment type, job function, industries)
     * come from a small set of controlled-vocabulary terms, but guest pages render them in
     * whatever locale LinkedIn picks via geo-IP (Arabic for Moroccan IPs) regardless of the
     * Accept-Language header. Since the vocabulary is fixed, we translate the terms we've
     * seen back to French to match the language of the other scraped sources. Any term not
     * in this table (new/unseen value, or already French/English) is returned unchanged.
     */
    private static final java.util.Map<String, String> LINKEDIN_AR_TO_FR = java.util.Map.ofEntries(
            // Seniority level
            java.util.Map.entry("غير مطبق", "Non précisé"),
            java.util.Map.entry("تدريب", "Stage"),
            java.util.Map.entry("مستوى المبتدئين", "Débutant"),
            java.util.Map.entry("معاون", "Associé"),
            java.util.Map.entry("مستوى متوسط الأقدمية", "Niveau intermédiaire/senior"),
            java.util.Map.entry("مدير", "Directeur"),
            java.util.Map.entry("تنفيذي", "Cadre dirigeant"),
            // Employment type
            java.util.Map.entry("دوام كامل", "Temps plein"),
            java.util.Map.entry("دوام جزئي", "Temps partiel"),
            java.util.Map.entry("عقد", "Contrat"),
            java.util.Map.entry("عمل مؤقت", "Intérim"),
            java.util.Map.entry("تطوع", "Bénévolat"),
            java.util.Map.entry("أخرى", "Autre"),
            // Job function / Industries (best-effort, extend as new values appear)
            java.util.Map.entry("غير ذلك", "Autre"),
            java.util.Map.entry("المشتريات و سلسلة التوريدات", "Achats et chaîne d'approvisionnement"),
            java.util.Map.entry("النفط والغاز", "Pétrole et gaz"),
            java.util.Map.entry("الهندسة و تكنولوجيا المعلومات", "Ingénierie et informatique"),
            java.util.Map.entry("الخدمات الهندسية", "Services d'ingénierie"),
            java.util.Map.entry("الإدارة و التصنيع", "Gestion et production"),
            java.util.Map.entry("المحاسبة / تدقيق الحسابات و مالية", "Comptabilité / Audit et finance"),
            java.util.Map.entry("تأمين الجودة", "Assurance qualité"),
            java.util.Map.entry("خدمات الموارد البشرية", "Services de ressources humaines"),
            java.util.Map.entry("الموارد البشرية", "Ressources humaines"),
            java.util.Map.entry("تطوير الأعمال التجارية و المبيعات", "Développement commercial et ventes"),
            java.util.Map.entry("المبيعات و تطوير الأعمال التجارية", "Ventes et développement commercial"),
            java.util.Map.entry("خدمات الرعاية الصحية", "Services de santé"),
            java.util.Map.entry("تكنولوجيا المعلومات", "Informatique")
    );

    private String toFrench(String value) {
        if (value == null) return null;
        return LINKEDIN_AR_TO_FR.getOrDefault(value, value);
    }

    /**
     * Recursively flattens an element's content to text: {@code <br>} → \n,
     * {@code <li>} → "- " prefixed line, {@code <p>}/{@code <ul>} → paragraph break.
     * Other elements (e.g. {@code <strong>}) are unwrapped in place.
     */
    private void htmlToText(Element el, StringBuilder sb) {
        for (Node node : el.childNodes()) {
            if (node instanceof TextNode tn) {
                sb.append(tn.text());
            } else if (node instanceof Element child) {
                switch (child.tagName()) {
                    case "br" -> sb.append("\n");
                    case "li" -> {
                        sb.append("\n- ");
                        htmlToText(child, sb);
                        sb.append("\n");
                    }
                    case "ul", "ol", "p" -> {
                        htmlToText(child, sb);
                        sb.append("\n\n");
                    }
                    default -> htmlToText(child, sb);
                }
            }
        }
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
