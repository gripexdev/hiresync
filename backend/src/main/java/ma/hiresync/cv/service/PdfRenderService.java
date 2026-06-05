package ma.hiresync.cv.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Renders an HTML document to a vector PDF using headless Chromium (Playwright).
 *
 * This is the same approach used by Resume.io / Novoresume / FlowCV:
 *   - The frontend builds the exact CV HTML (template + data + photo)
 *   - We feed that HTML to a real Chrome engine
 *   - Chrome produces a true vector PDF: selectable text, crisp at any zoom, ATS-readable
 *
 * The Playwright + Browser instances are expensive to create, so we keep ONE
 * browser alive for the whole app lifetime and open a fresh Page per request.
 */
@Service
@Slf4j
public class PdfRenderService {

    private Playwright playwright;
    private Browser    browser;

    @PostConstruct
    public void init() {
        try {
            this.playwright = Playwright.create();
            this.browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(true));
            log.info("Playwright Chromium launched — PDF rendering ready");
        } catch (Exception e) {
            log.error("Failed to launch Playwright Chromium: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (browser != null)    browser.close();
        if (playwright != null) playwright.close();
        log.info("Playwright Chromium closed");
    }

    /**
     * Convert a full HTML document into A4 PDF bytes.
     *
     * @param html complete HTML document (with inline CSS and base64 images)
     * @return PDF bytes (vector)
     */
    public byte[] htmlToPdf(String html) {
        if (browser == null) {
            throw new IllegalStateException("Chromium not available — PDF rendering disabled");
        }

        try (Page page = browser.newPage()) {
            // Load the HTML and wait until network is idle (fonts, images loaded)
            page.setContent(html, new Page.SetContentOptions()
                .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.NETWORKIDLE));

            // Render to A4 PDF — printBackground keeps colours/backgrounds
            return page.pdf(new Page.PdfOptions()
                .setFormat("A4")
                .setPrintBackground(true)
                .setMargin(new Margin()
                    .setTop("0").setBottom("0").setLeft("0").setRight("0")));
        } catch (Exception e) {
            log.error("PDF rendering failed: {}", e.getMessage());
            throw new RuntimeException("PDF rendering failed", e);
        }
    }
}
