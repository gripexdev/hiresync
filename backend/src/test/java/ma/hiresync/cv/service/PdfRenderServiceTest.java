package ma.hiresync.cv.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * PdfRenderService's @PostConstruct init() launches a real Chromium process,
 * which we deliberately never invoke in a unit test. Instead we inject a
 * mocked Playwright Browser/Page directly into the private fields via
 * ReflectionTestUtils, exercising htmlToPdf()'s actual logic (and its
 * "Chromium not available" guard) without touching a real browser.
 */
@ExtendWith(MockitoExtension.class)
class PdfRenderServiceTest {

    @Mock private Browser browser;
    @Mock private Page page;

    @Test
    void htmlToPdf_browserNeverLaunched_throwsIllegalState() {
        var service = new PdfRenderService();
        // browser field stays null — init() was never called, matching a failed Chromium launch.

        assertThatThrownBy(() -> service.htmlToPdf("<html></html>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chromium not available");
    }

    @Test
    void htmlToPdf_browserAvailable_returnsPdfBytesFromThePage() {
        var service = new PdfRenderService();
        ReflectionTestUtils.setField(service, "browser", browser);
        when(browser.newPage()).thenReturn(page);
        byte[] expectedBytes = "%PDF-1.4".getBytes();
        when(page.pdf(org.mockito.ArgumentMatchers.any())).thenReturn(expectedBytes);

        byte[] result = service.htmlToPdf("<html><body>CV</body></html>");

        assertThat(result).isEqualTo(expectedBytes);
    }

    @Test
    void htmlToPdf_pageThrows_wrapsInRuntimeException() {
        var service = new PdfRenderService();
        ReflectionTestUtils.setField(service, "browser", browser);
        when(browser.newPage()).thenReturn(page);
        when(page.pdf(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("Chromium crashed"));

        assertThatThrownBy(() -> service.htmlToPdf("<html></html>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("PDF rendering failed");
    }

    @Test
    void htmlToPdf_alwaysClosesThePageEvenOnFailure() {
        var service = new PdfRenderService();
        ReflectionTestUtils.setField(service, "browser", browser);
        when(browser.newPage()).thenReturn(page);
        when(page.pdf(org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("boom"));

        try {
            service.htmlToPdf("<html></html>");
        } catch (RuntimeException ignored) {
            // expected — we only care that the page (an AutoCloseable resource) gets closed.
        }

        org.mockito.Mockito.verify(page).close();
    }
}
