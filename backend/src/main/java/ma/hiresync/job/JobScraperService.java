package ma.hiresync.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScraperService {

    // ── Rekrut.ma selectors ──────────────────────────────────────────────────
    // Adjust these constants if the site updates its HTML structure
    private static final String SOURCE       = "rekrut.ma";
    private static final String BASE_URL     = "https://www.rekrut.ma";
    private static final String LISTINGS_URL = BASE_URL + "/offres-emploi";

    private static final String CARD_SEL     = "div.job-item, div.offer-item, article.job-card, div[class*='offer']";
    private static final String TITLE_SEL    = "h2, h3, .job-title, .offer-title, [class*='title']";
    private static final String COMPANY_SEL  = ".company, .employer, .company-name, [class*='company']";
    private static final String LOCATION_SEL = ".location, .city, [class*='location'], [class*='ville']";
    private static final String CONTRACT_SEL = ".contract, .type, [class*='contract'], [class*='contrat']";
    private static final String LINK_SEL     = "a[href]";

    private final JobRepository jobRepository;

    public int scrape() {
        log.info("Scraping jobs from {}", LISTINGS_URL);
        Document doc;
        try {
            doc = Jsoup.connect(LISTINGS_URL)
                    .userAgent("Mozilla/5.0 (compatible; HireSync-Bot/1.0)")
                    .timeout(15_000)
                    .get();
        } catch (IOException e) {
            log.error("Failed to fetch {}: {}", LISTINGS_URL, e.getMessage());
            return 0;
        }

        Elements cards = doc.select(CARD_SEL);
        log.info("Found {} job cards on page", cards.size());

        if (cards.isEmpty()) {
            // Log a snippet so selectors can be adjusted without redeploying
            log.warn("No cards matched '{}'. Page title: '{}'. First 500 chars of body: {}",
                    CARD_SEL, doc.title(), doc.body().text().substring(0, Math.min(500, doc.body().text().length())));
        }

        List<Job> toSave = new ArrayList<>();
        for (Element card : cards) {
            String url = resolveUrl(card.selectFirst(LINK_SEL));
            if (url == null || jobRepository.existsBySourceUrl(url)) continue;

            toSave.add(Job.builder()
                    .title(text(card, TITLE_SEL, "N/A"))
                    .company(text(card, COMPANY_SEL, null))
                    .location(text(card, LOCATION_SEL, null))
                    .contractType(text(card, CONTRACT_SEL, null))
                    .sourceUrl(url)
                    .source(SOURCE)
                    .scrapedAt(LocalDateTime.now())
                    .build());
        }

        jobRepository.saveAll(toSave);
        log.info("Saved {} new jobs from {}", toSave.size(), SOURCE);
        return toSave.size();
    }

    private String resolveUrl(Element anchor) {
        if (anchor == null) return null;
        String href = anchor.attr("href").trim();
        if (href.isEmpty()) return null;
        return href.startsWith("http") ? href : BASE_URL + href;
    }

    private String text(Element parent, String selector, String fallback) {
        Element el = parent.selectFirst(selector);
        if (el == null) return fallback;
        String t = el.text().trim();
        return t.isEmpty() ? fallback : t;
    }
}
