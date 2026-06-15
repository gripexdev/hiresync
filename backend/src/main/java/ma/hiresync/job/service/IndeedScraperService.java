package ma.hiresync.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Scrapes the Indeed Morocco search results page (server-rendered job cards
 * embedded as JSON in a {@code <script>} tag — no headless browser needed).
 *
 * Only page 1 is scraped: requesting {@code &start=10} (page 2+) redirects to
 * Indeed's sign-in wall ({@code secure.indeed.com/auth?...page-two-signin}),
 * so pagination beyond the first ~15 results isn't accessible without auth.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndeedScraperService {

    private static final String SOURCE   = "indeed.ma";
    private static final String BASE_URL = "https://ma.indeed.com";

    // Page 1 of the "offre d'emploi" search — page 2+ requires sign-in.
    private static final String SEARCH_URL = BASE_URL + "/jobs?q=offre+d%27emploi&l=";

    private static final String JSON_MARKER = "window.mosaic.providerData[\"mosaic-provider-jobcards\"]=";

    private final JobRepository jobRepository;
    private final ObjectMapper  objectMapper = new ObjectMapper();

    // ── Public entry point ────────────────────────────────────────────────────

    public int scrape() {
        log.info("Scraping indeed.ma → {}", SEARCH_URL);
        Document doc;
        try {
            doc = Jsoup.connect(SEARCH_URL)
                    .userAgent("Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)")
                    .header("Accept-Language", "fr-FR,fr;q=0.9")
                    .referrer(BASE_URL)
                    .timeout(15_000)
                    .get();
        } catch (IOException e) {
            log.error("Failed to fetch indeed.ma search page: {}", e.getMessage());
            return 0;
        }

        int saved = parsePage(doc);
        log.info("indeed.ma scrape finished — {} new jobs saved", saved);
        return saved;
    }

    // ── Page parser ───────────────────────────────────────────────────────────

    private int parsePage(Document doc) {
        JsonNode results = extractResults(doc);
        if (results == null || !results.isArray() || results.isEmpty()) {
            log.warn("No job results found on indeed.ma search page (title: '{}')", doc.title());
            return 0;
        }

        List<Job> toSave = new ArrayList<>();
        for (JsonNode r : results) {
            String jobkey = r.path("jobkey").asText(null);
            if (jobkey == null || jobkey.isBlank()) continue;

            // Indeed's SPA renders job details inline on the search page itself
            // (right-hand panel) — selected via the &vjk= query param. Direct
            // /viewjob?jk= requests get 403'd, but this URL serves the same
            // div#jobDescriptionText content and is reachable for enrichment.
            String sourceUrl = SEARCH_URL + "&vjk=" + jobkey;
            if (jobRepository.existsBySourceUrl(sourceUrl)) continue;

            String title = r.path("title").asText("").trim();
            if (title.isEmpty()) continue;

            String company = textOrNull(r.path("company"));
            String location = textOrNull(r.path("formattedLocation"));
            String contractType = extractContractType(r);

            String description = snippetToText(r.path("snippet").asText(null));

            LocalDateTime postedAt = null;
            JsonNode pubDate = r.path("pubDate");
            if (pubDate.isNumber()) {
                postedAt = Instant.ofEpochMilli(pubDate.asLong())
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            toSave.add(Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .contractType(contractType)
                    .description(description)
                    .sourceUrl(sourceUrl)
                    .source(SOURCE)
                    .postedAt(postedAt)
                    .scrapedAt(LocalDateTime.now())
                    .build());
        }

        jobRepository.saveAll(toSave);
        return toSave.size();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Locates the {@code <script>} tag holding the job cards and parses its embedded JSON object. */
    private JsonNode extractResults(Document doc) {
        for (Element script : doc.select("script")) {
            String data = script.data();
            int idx = data.indexOf(JSON_MARKER);
            if (idx == -1) continue;

            String json = extractJsonObject(data, idx + JSON_MARKER.length());
            if (json == null) continue;

            try {
                JsonNode root = objectMapper.readTree(json);
                return root.path("metaData").path("mosaicProviderJobCardsModel").path("results");
            } catch (Exception e) {
                log.warn("Failed to parse indeed.ma job cards JSON: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Returns the balanced {@code {...}} substring of {@code src} starting at {@code start}, or null. */
    private String extractJsonObject(String src, int start) {
        if (start >= src.length() || src.charAt(start) != '{') return null;
        int depth = 0;
        for (int i = start; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return src.substring(start, i + 1);
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node) {
        String text = node.asText("").trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * The {@code jobTypes} array is empty for most results; the same information
     * ("Temps plein", "CDI"...) is usually still present under
     * {@code taxonomyAttributes[].label == "job-types"}. Falls back to that, and
     * returns {@code null} (rather than an empty string) if neither is present.
     */
    private String extractContractType(JsonNode r) {
        String fromJobTypes = StreamSupport.stream(r.path("jobTypes").spliterator(), false)
                .map(JsonNode::asText)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
        if (!fromJobTypes.isEmpty()) return fromJobTypes;

        for (JsonNode taxo : r.path("taxonomyAttributes")) {
            if (!"job-types".equals(taxo.path("label").asText())) continue;
            String fromTaxo = StreamSupport.stream(taxo.path("attributes").spliterator(), false)
                    .map(a -> a.path("label").asText(""))
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.joining(", "));
            return fromTaxo.isEmpty() ? null : fromTaxo;
        }
        return null;
    }

    /** Converts the card's HTML {@code snippet} (a {@code <ul><li>} list, occasionally with stray text) to plain text. */
    private String snippetToText(String html) {
        if (html == null || html.isBlank()) return null;
        Element body = Jsoup.parseBodyFragment(html).body();
        StringBuilder sb = new StringBuilder();
        for (Element child : body.children()) {
            if (child.tagName().equals("ul")) {
                for (Element li : child.select("li")) {
                    String text = li.text().trim();
                    if (!text.isEmpty()) sb.append("- ").append(text).append("\n");
                }
            } else {
                String text = child.text().trim();
                if (!text.isEmpty()) sb.append(text).append("\n");
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }
}
