package ma.hiresync.cv.controller;

import ma.hiresync.auth.service.JwtService;
import ma.hiresync.cv.dto.*;
import ma.hiresync.cv.entity.CvOptimization.OptimizationStatus;
import ma.hiresync.cv.service.CvService;
import ma.hiresync.cv.service.PdfRenderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvControllerTest {

    @Mock private CvService cvService;
    @Mock private JwtService jwtService;
    @Mock private PdfRenderService pdfRenderService;

    private CvController controller() {
        return new CvController(cvService, jwtService, pdfRenderService);
    }

    private static final String AUTH = "Bearer some-jwt";

    private static OptimizationResponse sampleOptimization() {
        return new OptimizationResponse(UUID.randomUUID(), "completed", UUID.randomUUID(), "job-1",
                "Backend Dev", "Acme", 61, 92, 84, null, "Dev", "Backend Dev",
                List.of("Java"), List.of(), List.of(), null, "Groq", 4200L,
                java.time.Instant.now(), java.time.Instant.now(), null, false);
    }

    @Test
    void getVersions_extractsUserIdFromBearerToken() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<CvResponse> page = new PageImpl<>(List.of());
        when(cvService.getAllCvs(eq(userId), any())).thenReturn(page);

        var response = controller().getVersions(0, 20, AUTH);

        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void upload_delegatesFileAndUserIdToService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var file = new MockMultipartFile("file", "cv.pdf", "application/pdf", "content".getBytes());
        var expected = new CvResponse(UUID.randomUUID(), "cv.pdf", 10L, "application/pdf",
                java.time.Instant.now(), 70, true, List.of());
        when(cvService.upload(file, userId)).thenReturn(expected);

        var response = controller().upload(file, AUTH);

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void activate_delegatesAndReturns204() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);

        var response = controller().activate(cvId, AUTH);

        verify(cvService).activate(cvId, userId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void delete_delegatesAndReturns204() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);

        var response = controller().delete(cvId, AUTH);

        verify(cvService).delete(cvId, userId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void optimize_returns202AcceptedWithTriggerResponse() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var req = new OptimizeRequest(UUID.randomUUID(), "job-1", "Backend Dev", "Acme", "desc");
        var trigger = new OptimizeTriggerResponse(UUID.randomUUID(), "queued", "En file d'attente", false);
        when(cvService.triggerOptimization(req, userId)).thenReturn(trigger);

        var response = controller().optimize(req, AUTH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(trigger);
    }

    @Test
    void getOptimization_delegatesToService() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var expected = sampleOptimization();
        when(cvService.getOptimization(optimId, userId)).thenReturn(expected);

        var response = controller().getOptimization(optimId, AUTH);

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void boostKeywords_passesKeywordListFromRequestBody() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var expected = sampleOptimization();
        when(cvService.boostKeywords(optimId, userId, List.of("Docker"))).thenReturn(expected);

        var response = controller().boostKeywords(optimId, new BoostKeywordsRequest(List.of("Docker")), AUTH);

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void coverLetter_defaultsRegenerateToFalse() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var letter = new CoverLetterResponse("Candidature", "Bonjour...", "Groq");
        when(cvService.generateCoverLetter(optimId, userId, false)).thenReturn(letter);

        var response = controller().coverLetter(optimId, false, AUTH);

        assertThat(response.getBody()).isEqualTo(letter);
    }

    @Test
    void getHistory_blankStatus_passesNullToService() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<OptimizationResponse> page = new PageImpl<>(List.of());
        when(cvService.getHistory(eq(userId), isNull(), eq("acme"), any())).thenReturn(page);

        var response = controller().getHistory(null, "acme", 0, 10, AUTH);

        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void getHistory_validStatus_parsesEnumCaseInsensitively() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<OptimizationResponse> page = new PageImpl<>(List.of());
        when(cvService.getHistory(eq(userId), eq(OptimizationStatus.REJECTED), any(), any())).thenReturn(page);

        var response = controller().getHistory("rejected", null, 0, 10, AUTH);

        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void getHistory_allKeyword_meansNoFilter() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<OptimizationResponse> page = new PageImpl<>(List.of());
        when(cvService.getHistory(eq(userId), isNull(), any(), any())).thenReturn(page);

        controller().getHistory("all", null, 0, 10, AUTH);

        verify(cvService).getHistory(eq(userId), isNull(), any(), any());
    }

    @Test
    void getHistory_invalidStatus_throwsIllegalArgument() {
        when(jwtService.extractUserId("some-jwt")).thenReturn(UUID.randomUUID());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> controller().getHistory("bogus", null, 0, 10, AUTH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getHistoryStats_delegatesToService() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var stats = new HistoryStatsResponse(10, 6, 2, 2, 21, 92);
        when(cvService.getHistoryStats(userId)).thenReturn(stats);

        var response = controller().getHistoryStats(AUTH);

        assertThat(response.getBody()).isEqualTo(stats);
    }

    @Test
    void getStructuredCv_delegatesToService() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        when(cvService.getStructuredCv(optimId, userId)).thenReturn(java.util.Map.of("fullName", "Othmane"));

        var response = controller().getStructuredCv(optimId, AUTH);

        assertThat(response.getBody()).isEqualTo(java.util.Map.of("fullName", "Othmane"));
    }

    @Test
    void downloadOptimizedCv_returnsPdfContentTypeAndAttachmentHeader() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        byte[] pdfBytes = "%PDF-1.4".getBytes();
        when(cvService.generateOptimizedCvPdf(optimId, userId)).thenReturn(pdfBytes);

        var response = controller().downloadOptimizedCv(optimId, AUTH);

        assertThat(response.getBody()).isEqualTo(pdfBytes);
        assertThat(response.getHeaders().getContentDisposition().toString()).contains("attachment");
    }

    @Test
    void renderPdf_usesDefaultFileNameWhenNoneProvided() {
        when(jwtService.extractUserId("some-jwt")).thenReturn(UUID.randomUUID());
        byte[] pdfBytes = "%PDF-1.4".getBytes();
        when(pdfRenderService.htmlToPdf("<html></html>")).thenReturn(pdfBytes);

        var response = controller().renderPdf(new RenderPdfRequest("<html></html>", null), AUTH);

        assertThat(response.getBody()).isEqualTo(pdfBytes);
        assertThat(response.getHeaders().getContentDisposition().toString()).contains("CV_HireSync.pdf");
    }

    @Test
    void renderPdf_usesProvidedFileName() {
        when(jwtService.extractUserId("some-jwt")).thenReturn(UUID.randomUUID());
        when(pdfRenderService.htmlToPdf(anyString())).thenReturn(new byte[0]);

        var response = controller().renderPdf(new RenderPdfRequest("<html></html>", "MonCV.pdf"), AUTH);

        assertThat(response.getHeaders().getContentDisposition().toString()).contains("MonCV.pdf");
    }
}
