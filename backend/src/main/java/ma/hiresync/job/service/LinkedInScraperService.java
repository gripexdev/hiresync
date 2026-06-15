package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes LinkedIn's public "guest" job search endpoint — no login required.
 * Returns plain HTML job cards (no embedded JSON, unlike indeed.ma), and the
 * linked {@code /jobs/view/...} detail pages are publicly readable too.
 *
 * Pages through {@code &start=0,10,20,...} up to {@link #MAX_PAGES} pages
 * (10 results each). LinkedIn's anti-bot can throttle/empty out guest
 * pagination unpredictably, so we stop as soon as a page comes back empty
 * or fails — whatever was gathered from earlier pages is still saved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkedInScraperService {

    private static final String SOURCE   = "linkedin.com";
    private static final String BASE_URL = "https://www.linkedin.com";

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGES = 3;

    // Guest job search — &start=0,10,20,... for subsequent pages.
    private static final String SEARCH_URL =
            BASE_URL + "/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=offre+d%27emploi&location=Morocco&start=";

    private final JobRepository jobRepository;

    // ── Public entry point ────────────────────────────────────────────────────

    public int scrape() {
        int saved = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            int start = page * PAGE_SIZE;
            String url = SEARCH_URL + start;
            log.info("Scraping linkedin.com → {}", url);

            Document doc;
            try {
                doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)")
                        .header("Accept-Language", "fr-FR,fr;q=0.9")
                        .referrer(BASE_URL)
                        .timeout(15_000)
                        .get();
            } catch (IOException e) {
                log.warn("Failed to fetch linkedin.com search page (start={}): {} — stopping pagination", start, e.getMessage());
                break;
            }

            int savedOnPage = parsePage(doc);
            saved += savedOnPage;

            if (doc.select("div.base-search-card").isEmpty()) {
                log.info("linkedin.com page (start={}) had no job cards — stopping pagination", start);
                break;
            }
        }

        log.info("linkedin.com scrape finished — {} new jobs saved", saved);
        return saved;
    }

    // ── Page parser ───────────────────────────────────────────────────────────

    private int parsePage(Document doc) {
        var cards = doc.select("div.base-search-card");

        List<Job> toSave = new ArrayList<>();
        for (Element card : cards) {
            Element link = card.selectFirst("a.base-card__full-link");
            if (link == null) continue;

            // Strip tracking query params (?position=...&pageNum=...&refId=...) for a stable dedup key.
            String sourceUrl = link.attr("href").split("\\?")[0];
            if (sourceUrl.isEmpty() || jobRepository.existsBySourceUrl(sourceUrl)) continue;

            Element titleEl = card.selectFirst("h3.base-search-card__title");
            if (titleEl == null) continue;
            String title = titleEl.text().trim();
            if (title.isEmpty()) continue;

            String company = textOrNull(card.selectFirst("h4.base-search-card__subtitle"));
            String location = textOrNull(card.selectFirst("span.job-search-card__location"));

            LocalDateTime postedAt = null;
            Element timeEl = card.selectFirst("time.job-search-card__listdate");
            if (timeEl != null) {
                postedAt = parseDate(timeEl.attr("datetime").trim());
            }

            String logoUrl = attrOrNull(card.selectFirst("img.artdeco-entity-image"), "data-delayed-url");

            toSave.add(Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .logoUrl(logoUrl)
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

    private String textOrNull(Element el) {
        if (el == null) return null;
        String text = el.text().trim();
        return text.isEmpty() ? null : text;
    }

    /** Reads an attribute (e.g. the company logo's {@code data-delayed-url}), tolerating a missing element/attribute. */
    private String attrOrNull(Element el, String attr) {
        if (el == null) return null;
        String value = el.attr(attr).trim();
        return value.isEmpty() ? null : value;
    }

    /** Parse "2026-06-10" (the {@code <time datetime>} attribute) → LocalDateTime (midnight) */
    private LocalDateTime parseDate(String raw) {
        try {
            return LocalDate.parse(raw).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
}
