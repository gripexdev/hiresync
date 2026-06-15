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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobScraperService {

    private static final String SOURCE    = "rekrute.com";
    private static final String BASE_URL  = "https://www.rekrute.com";

    // Page 1 — Morocco-specific listing
    private static final String PAGE1_URL = BASE_URL + "/offres-emploi-maroc.html";
    // Pages 2+ — paginated (s=1 → 10/page, o=1 → newest first)
    private static final String PAGE_N_URL = BASE_URL + "/offres.html?s=1&p=%d&o=1";

    // How many pages to scrape per trigger (10 jobs per page)
    private static final int MAX_PAGES = 3;

    private final JobRepository jobRepository;

    // ── Public entry point ────────────────────────────────────────────────────

    public int scrape() {
        int totalSaved = 0;
        for (int page = 1; page <= MAX_PAGES; page++) {
            String url = (page == 1) ? PAGE1_URL : String.format(PAGE_N_URL, page);
            log.info("Scraping rekrute.com page {} → {}", page, url);
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; HireSync-Bot/1.0; +https://hiresync.ma)")
                        .referrer("https://www.rekrute.com")
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
        // Each job is: <li class="post-id" id="183362">
        Elements cards = doc.select("li.post-id");
        if (cards.isEmpty()) {
            log.warn("No job cards found on page '{}'. First 300 chars: {}",
                    doc.title(),
                    doc.body().text().substring(0, Math.min(300, doc.body().text().length())));
            return 0;
        }

        List<Job> toSave = new ArrayList<>();
        for (Element card : cards) {

            // ── Source URL (dedup key) ─────────────────────────────────────
            Element titleLink = card.selectFirst("a.titreJob");
            if (titleLink == null) continue;
            String path = titleLink.attr("href").trim();
            if (path.isEmpty()) continue;
            String sourceUrl = path.startsWith("http") ? path : BASE_URL + path;
            if (jobRepository.existsBySourceUrl(sourceUrl)) continue;

            // ── Title + Location ───────────────────────────────────────────
            // Format: "Chargé(e) d´assistance francophone | Casablanca (Maroc)"
            String fullText = titleLink.text().trim();
            String title    = fullText;
            String location = null;
            int pipe = fullText.lastIndexOf(" | ");
            if (pipe > 0) {
                title    = fullText.substring(0, pipe).trim();
                location = fullText.substring(pipe + 3)
                        .replaceAll("\\s*\\([^)]*\\)\\s*$", "")  // remove "(Maroc)"
                        .trim();
            }
            if (title.isEmpty()) continue;

            // ── Company + Logo ─────────────────────────────────────────────
            // <img class="photo" src="/rekrute/file/jobOfferLogo/..." alt="Company Name">
            // Confidential: src contains "confidentiel", alt is empty
            String company = null;
            String logoUrl  = null;
            Element logoImg = card.selectFirst("img.photo");
            if (logoImg != null) {
                String src = logoImg.attr("src");
                String alt = logoImg.attr("alt").trim();
                if (!src.contains("confidentiel") && !alt.isEmpty()) {
                    company = alt;
                    String postId = card.id(); // li id="183362"
                    if (!postId.isEmpty()) {
                        logoUrl = BASE_URL + "/rekrute/file/jobOfferLogo/jobOfferId/" + postId;
                    }
                }
            }

            // ── Description (AI summary inside first div.info > span) ──────
            // <div class="info" style="margin:..."><img .../><span>Description...</span></div>
            String description = null;
            for (Element info : card.select("div.info")) {
                Element span = info.selectFirst("span");
                if (span != null && !span.text().trim().isEmpty()) {
                    description = span.text().trim();
                    break;
                }
            }

            // ── Publication date: <em class="date"><span>06/06/2026</span> ─
            LocalDateTime postedAt = null;
            Element dateSpan = card.selectFirst("em.date span");
            if (dateSpan != null) {
                postedAt = parseDate(dateSpan.text().trim());
            }

            // ── Metadata from the info <ul> ────────────────────────────────
            String contractType   = firstLinkText(card, "a[href*='contractType']");
            String sector         = firstLinkText(card, "a[href*='sectorId']");
            String experienceLevel= firstLinkText(card, "a[href*='workExperienceId']");

            toSave.add(Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .contractType(contractType)
                    .sector(sector)
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

    private String firstLinkText(Element parent, String selector) {
        Element el = parent.selectFirst(selector);
        if (el == null) return null;
        String t = el.text().trim();
        return t.isEmpty() ? null : t;
    }

    /** Parse "06/06/2026" → LocalDateTime (midnight) */
    private LocalDateTime parseDate(String raw) {
        try {
            return LocalDate.parse(raw, DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }
}
