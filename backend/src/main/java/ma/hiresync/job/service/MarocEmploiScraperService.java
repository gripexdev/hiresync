package ma.hiresync.job.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrapes marocemploi.net — a WordPress/WP-JobSearch site.
 *
 * Pagination: page 1 is at /offre/, pages 2+ at /offre/?ajax_filter=true&job_page=N.
 * Both render full server-side HTML (no headless browser needed).
 * Each new job's detail page is fetched inline to get contractType, description,
 * and exact publication date (only relative "Publié X jours" is on the card).
 */
@Service
@Slf4j
public class MarocEmploiScraperService {

    /** Injected from FLARESOLVERR_URL env var; empty/null = try direct Jsoup (non-Docker only). */
    @Value("${FLARESOLVERR_URL:}")
    private String flareSolverrUrl;

    private static final String SOURCE    = "marocemploi.net";
    private static final String BASE_URL  = "https://www.marocemploi.net";
    private static final String PAGE1_URL = BASE_URL + "/offre/";
    private static final String PAGE_N_URL = BASE_URL + "/offre/?ajax_filter=true&job_page=%d";

    private static final int MAX_PAGES = 3;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // Placeholder images — not a real company logo
    private static final List<String> PLACEHOLDER_PATTERNS =
            List.of("pasdimage", "profile-img-10", "default-logo", "no-logo");

    private static final Map<String, Integer> FR_MONTHS = Map.ofEntries(
            Map.entry("janvier", 1),  Map.entry("février", 2),  Map.entry("fevrier", 2),
            Map.entry("mars", 3),     Map.entry("avril", 4),     Map.entry("mai", 5),
            Map.entry("juin", 6),     Map.entry("juillet", 7),   Map.entry("août", 8),
            Map.entry("aout", 8),     Map.entry("septembre", 9), Map.entry("octobre", 10),
            Map.entry("novembre", 11),Map.entry("décembre", 12), Map.entry("decembre", 12)
    );
    private static final Pattern DATE_PATTERN =
            Pattern.compile("(\\d{1,2})\\s+(\\p{L}+)\\s+(\\d{4})");

    private final JobRepository  jobRepository;
    private final ObjectMapper   objectMapper = new ObjectMapper();

    public MarocEmploiScraperService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public int scrape() {
        int totalSaved = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            String url = (page == 1) ? PAGE1_URL : String.format(PAGE_N_URL, page);
            log.info("Scraping marocemploi.net page {} → {}", page, url);
            try {
                Document doc = fetch(url);
                int saved = parsePage(doc);
                totalSaved += saved;
                log.info("marocemploi.net page {}: {} new jobs saved", page, saved);
            } catch (IOException e) {
                log.error("Failed to fetch marocemploi.net page {}: {}", page, e.getMessage());
                break;
            }
        }
        log.info("marocemploi.net scrape finished — {} new jobs total", totalSaved);
        return totalSaved;
    }

    // ── Page parser ───────────────────────────────────────────────────────────

    private int parsePage(Document doc) {
        // Each job lives in .jobsearch-joblisting-classic-wrap which holds
        // a <figure> (logo) + .jobsearch-list-option (all text fields).
        Elements wrappers = doc.select(".jobsearch-joblisting-classic-wrap");
        if (wrappers.isEmpty()) {
            log.warn("No job wrappers on marocemploi.net page '{}'. Body preview: {}",
                    doc.title(),
                    doc.body().text().substring(0, Math.min(200, doc.body().text().length())));
            return 0;
        }

        List<Job> toSave = new ArrayList<>();
        for (Element wrapper : wrappers) {
            Job job = parseCard(wrapper);
            if (job != null) toSave.add(job);
        }

        jobRepository.saveAll(toSave);
        return toSave.size();
    }

    // ── Card parser ───────────────────────────────────────────────────────────

    private Job parseCard(Element wrapper) {
        Element card = wrapper.selectFirst(".jobsearch-list-option");
        if (card == null) return null;

        // ── Source URL (dedup key) ─────────────────────────────────────────
        Element titleLink = card.selectFirst("h2.jobsearch-pst-title a");
        if (titleLink == null) return null;
        String sourceUrl = titleLink.attr("href").trim();
        if (sourceUrl.isEmpty()) return null;
        if (jobRepository.existsBySourceUrl(sourceUrl)) return null;

        // ── Title ─────────────────────────────────────────────────────────
        String title = titleLink.text().trim();
        // Titles often embed city: "Comptable – Casablanca" → keep as-is
        if (title.isEmpty()) return null;

        // ── Company ───────────────────────────────────────────────────────
        String company = null;
        Element companyEl = card.selectFirst("li.job-company-name a");
        if (companyEl != null) {
            String t = companyEl.text().replace("@", "").trim();
            if (!t.isEmpty()) company = t;
        }

        // ── Logo (in the sibling <figure> inside the same wrapper) ────────
        String logoUrl = extractLogo(wrapper);

        // ── Location (first classless <li> that is not the date) ──────────
        String location = null;
        Elements lis = card.select("ul > li");
        for (Element li : lis) {
            if (!li.className().isBlank()) continue;          // skip company-name li
            String text = li.text().trim();
            if (text.isEmpty() || text.startsWith("Publié")) continue;
            // If it looks like a sector (no digits, no comma) skip for now
            if (!text.matches(".*[,\\d].*")) continue;
            // "Casablanca, 20000" or "Rue X, Casablanca, 20000" → first segment
            int comma = text.indexOf(',');
            location = (comma > 0 ? text.substring(0, comma) : text).trim();
            break;
        }

        // ── Sector (last classless <li> after the date) ───────────────────
        String sectorFromCard = null;
        for (int i = lis.size() - 1; i >= 0; i--) {
            Element li = lis.get(i);
            if (!li.className().isBlank()) continue;
            String text = li.text().trim();
            if (text.isEmpty() || text.startsWith("Publié")) continue;
            if (text.matches(".*[,\\d].*")) continue;        // skip location-like
            sectorFromCard = text;
            break;
        }

        // ── Fetch detail page ─────────────────────────────────────────────
        String contractType = null;
        String sector       = sectorFromCard;
        String description  = null;
        LocalDateTime postedAt = null;

        try {
            Document detail = fetch(sourceUrl);

            // Contract type: .jobsearch-jobdetail-type → "CDI", "CDD", etc.
            Element typeEl = detail.selectFirst(".jobsearch-jobdetail-type");
            if (typeEl != null) {
                String t = typeEl.text().trim();
                if (!t.isEmpty()) contractType = t;
            }

            // Sector from detail is more reliable (card li order can shift)
            Element sectorEl = detail.selectFirst(".post-in-category");
            if (sectorEl != null) {
                String s = sectorEl.text().replaceFirst("(?i)^dans\\s*", "").trim();
                if (!s.isEmpty()) sector = s;
            }

            // Logo from detail if listing had a placeholder
            if (logoUrl == null) {
                Element logoImg = detail.selectFirst(".jobsearch-company-logo img");
                if (logoImg != null) logoUrl = cleanLogo(logoImg.attr("src").trim());
            }

            // Description: .jobsearch-description (plain text, may include bullet lists)
            Element descEl = detail.selectFirst(".jobsearch-description");
            if (descEl != null) {
                String t = descEl.text().trim();
                if (!t.isEmpty()) description = t;
            }

            // Publication date: "Date de Parution : 1 juin 2026"
            for (Element li : detail.select("li")) {
                if (li.text().contains("Date de Parution")) {
                    postedAt = parseFrenchDate(li.text());
                    break;
                }
            }

        } catch (IOException e) {
            log.warn("Could not fetch marocemploi.net detail page {}: {}", sourceUrl, e.getMessage());
        }

        return Job.builder()
                .title(title)
                .company(company)
                .location(location)
                .contractType(contractType)
                .sector(sector)
                .description(description)
                .logoUrl(logoUrl)
                .sourceUrl(sourceUrl)
                .source(SOURCE)
                .postedAt(postedAt)
                .scrapedAt(LocalDateTime.now())
                .enriched(description != null)
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fetches {@code url} and returns a parsed Jsoup Document.
     * Routes through FlareSolverr when FLARESOLVERR_URL is set (Docker mode)
     * to bypass Cloudflare's JA3-based bot detection.
     * Falls back to a direct Jsoup connection otherwise (local/dev mode).
     */
    private Document fetch(String url) throws IOException {
        if (flareSolverrUrl != null && !flareSolverrUrl.isBlank()) {
            return fetchViaFlareSolverr(url);
        }
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                .header("Cache-Control", "no-cache")
                .header("sec-ch-ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                .header("sec-ch-ua-mobile", "?0")
                .header("sec-ch-ua-platform", "\"Windows\"")
                .header("sec-fetch-dest", "document")
                .header("sec-fetch-mode", "navigate")
                .header("sec-fetch-site", "none")
                .header("sec-fetch-user", "?1")
                .referrer(BASE_URL)
                .timeout(20_000)
                .followRedirects(true)
                .get();
    }

    /**
     * Sends the URL to FlareSolverr, which uses headless Chromium to solve
     * Cloudflare JS challenges, and parses the returned HTML with Jsoup.
     */
    private Document fetchViaFlareSolverr(String targetUrl) throws IOException {
        String apiUrl = flareSolverrUrl + "/v1";
        String body = String.format(
                "{\"cmd\":\"request.get\",\"url\":\"%s\",\"maxTimeout\":60000}", targetUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(70_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("FlareSolverr returned HTTP " + status + " for " + targetUrl);
        }

        JsonNode root = objectMapper.readTree(conn.getInputStream());
        String fsStatus = root.path("status").asText("");
        if (!"ok".equals(fsStatus)) {
            throw new IOException("FlareSolverr error: " + root.path("message").asText("unknown"));
        }

        String html = root.path("solution").path("response").asText("");
        String resolvedUrl = root.path("solution").path("url").asText(targetUrl);
        if (html.isBlank()) {
            throw new IOException("FlareSolverr returned empty HTML for " + targetUrl);
        }
        return Jsoup.parse(html, resolvedUrl);
    }

    private String extractLogo(Element wrapper) {
        Element img = wrapper.selectFirst("figure img");
        if (img == null) return null;
        return cleanLogo(img.attr("src").trim());
    }

    private String cleanLogo(String src) {
        if (src == null || src.isEmpty()) return null;
        for (String placeholder : PLACEHOLDER_PATTERNS) {
            if (src.contains(placeholder)) return null;
        }
        return src;
    }

    /** Parse "Date de Parution : 1 juin 2026" → LocalDateTime at midnight. */
    private LocalDateTime parseFrenchDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        if (!m.find()) return null;
        int day = Integer.parseInt(m.group(1));
        Integer month = FR_MONTHS.get(m.group(2).toLowerCase());
        if (month == null) return null;
        int year = Integer.parseInt(m.group(3));
        try {
            return LocalDate.of(year, month, day).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
}
