package ma.hiresync.job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmploiMaScraperService {

    private static final String SOURCE   = "emploi.ma";
    private static final String BASE_URL = "https://www.emploi.ma";

    // Page 1 — default listing
    private static final String PAGE1_URL  = BASE_URL + "/recherche-jobs-maroc";
    // Pages 2+ — paginated (page=1 → page 2, page=2 → page 3, ...)
    private static final String PAGE_N_URL = BASE_URL + "/recherche-jobs-maroc?page=%d";

    // How many pages to scrape per trigger
    private static final int MAX_PAGES = 3;

    private final JobRepository jobRepository;

    // ── Public entry point ────────────────────────────────────────────────────

    public int scrape() {
        int totalSaved = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            String url = (page == 1) ? PAGE1_URL : String.format(PAGE_N_URL, page - 1);
            log.info("Scraping emploi.ma page {} → {}", page, url);
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)")
                        .referrer("https://www.emploi.ma")
                        .timeout(15_000)
                        .get();
                int saved = parsePage(doc);
                totalSaved += saved;
                log.info("Page {}: {} new jobs saved", page, saved);
            } catch (IOException e) {
                log.error("Failed to fetch page {}: {}", page, e.getMessage());
                break;
            }
        }
        log.info("Scrape finished — {} new jobs total", totalSaved);
        return totalSaved;
    }

    // ── Page parser ───────────────────────────────────────────────────────────

    private int parsePage(Document doc) {
        // Each job is: <div class="card card-job featured" data-href="https://www.emploi.ma/offre-emploi-maroc/...">
        Elements cards = doc.select("div.card.card-job");
        if (cards.isEmpty()) {
            log.warn("No job cards found on page '{}'. First 300 chars: {}",
                    doc.title(),
                    doc.body().text().substring(0, Math.min(300, doc.body().text().length())));
            return 0;
        }

        List<Job> toSave = new ArrayList<>();
        for (Element card : cards) {

            // ── Source URL (dedup key) ─────────────────────────────────────
            String sourceUrl = card.attr("data-href").trim();
            if (sourceUrl.isEmpty()) continue;
            if (jobRepository.existsBySourceUrl(sourceUrl)) continue;

            // ── Title ─────────────────────────────────────────────────────
            Element titleLink = card.selectFirst("h3 > a");
            if (titleLink == null) continue;
            String title = titleLink.text().trim();
            if (title.isEmpty()) continue;

            // ── Company ───────────────────────────────────────────────────
            // <a href="/recruteur/..." class="card-job-company company-name">Company</a>
            // Confidential listings have no such link ("N.C." text instead).
            String company = null;
            Element companyLink = card.selectFirst("a.card-job-company");
            if (companyLink != null) {
                String text = companyLink.text().trim();
                if (!text.isEmpty()) company = text;
            }

            // ── Logo ──────────────────────────────────────────────────────
            // Placeholder logos use "logo-non-dispo.jpg" / "default-logo.png".
            String logoUrl = null;
            Element logoImg = card.selectFirst("picture img");
            if (logoImg != null) {
                String src = logoImg.attr("src").trim();
                if (!src.isEmpty()
                        && !src.contains("logo-non-dispo")
                        && !src.contains("default-logo")) {
                    logoUrl = src;
                }
            }

            // ── Description (short summary shown on the card) ───────────────
            String description = null;
            Element descP = card.selectFirst("div.card-job-description p");
            if (descP != null) {
                String text = descP.text().trim();
                if (!text.isEmpty()) description = text;
            }

            // ── Publication date: <time datetime="2026-06-10">10.06.2026</time> ─
            LocalDateTime postedAt = null;
            Element timeEl = card.selectFirst("time[datetime]");
            if (timeEl != null) {
                postedAt = parseDate(timeEl.attr("datetime").trim());
            }

            // ── Metadata from the info <ul> ────────────────────────────────
            String contractType    = metaValue(card, "Contrat proposé");
            String experienceLevel = metaValue(card, "Niveau d'expérience");
            String location        = metaValue(card, "Région de");

            toSave.add(Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .contractType(contractType)
                    .experienceLevel(experienceLevel)
                    .description(description)
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

    /** Finds the metadata {@code <li>} whose text starts with {@code label} and returns its {@code <strong>} text. */
    private String metaValue(Element card, String label) {
        for (Element li : card.select("ul > li")) {
            Element strong = li.selectFirst("strong");
            if (strong == null) continue;
            if (li.text().trim().startsWith(label)) {
                String t = strong.text().trim();
                return t.isEmpty() ? null : t;
            }
        }
        return null;
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
