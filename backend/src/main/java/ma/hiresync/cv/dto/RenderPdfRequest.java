package ma.hiresync.cv.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/cv/render-pdf.
 * The frontend sends the fully-rendered CV HTML (template + data + photo)
 * and the backend converts it to a vector PDF via headless Chromium.
 */
public record RenderPdfRequest(
        @NotBlank String html,
        String fileName     // optional, defaults to CV_HireSync.pdf
) {}
