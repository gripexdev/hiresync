package ma.hiresync.application.controller;

import ma.hiresync.application.dto.ApplicationResponse;
import ma.hiresync.application.dto.ApplicationStatsResponse;
import ma.hiresync.application.dto.ApplyRequest;
import ma.hiresync.application.dto.UpdateStatusRequest;
import ma.hiresync.application.entity.JobApplication.ApplicationStatus;
import ma.hiresync.application.service.ApplicationService;
import ma.hiresync.auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    @Mock private ApplicationService applicationService;
    @Mock private JwtService jwtService;

    private ApplicationController controller() {
        return new ApplicationController(applicationService, jwtService);
    }

    private static final String AUTH = "Bearer some-jwt";

    private static ApplicationResponse sampleResponse() {
        return new ApplicationResponse(UUID.randomUUID(), UUID.randomUUID(), "Développeur Backend", "Acme",
                "Casablanca", UUID.randomUUID(), "cv.pdf", "applied", null, 70,
                java.time.Instant.now(), null);
    }

    @Test
    void apply_extractsUserIdFromBearerHeaderAndReturns201() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var req = new ApplyRequest(UUID.randomUUID(), "Motivé");
        var expected = sampleResponse();
        when(applicationService.apply(userId, jobId, req)).thenReturn(expected);

        var response = controller().apply(jobId, req, AUTH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void markApplied_extractsCvIdFromRequestBody() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var expected = sampleResponse();
        when(applicationService.markApplied(userId, jobId, cvId)).thenReturn(expected);

        var response = controller().markApplied(jobId, new ApplyRequest(cvId, null), AUTH);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void updateStatus_delegatesRawStatusStringToService() {
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var expected = sampleResponse();
        when(applicationService.updateStatus(userId, appId, "interview")).thenReturn(expected);

        var response = controller().updateStatus(appId, new UpdateStatusRequest("interview"), AUTH);

        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void myApplications_noStatusFilter_passesNullToService() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<ApplicationResponse> page = new PageImpl<>(List.of());
        when(applicationService.getMyApplications(eq(userId), isNull(), any())).thenReturn(page);

        var response = controller().myApplications(null, 0, 20, AUTH);

        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void myApplications_withStatusFilter_parsesEnumCaseInsensitively() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<ApplicationResponse> page = new PageImpl<>(List.of());
        when(applicationService.getMyApplications(eq(userId), eq(ApplicationStatus.INTERVIEW), any())).thenReturn(page);

        var response = controller().myApplications("interview", 0, 20, AUTH);

        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void myApplications_invalidStatus_throwsIllegalArgument() {
        when(jwtService.extractUserId("some-jwt")).thenReturn(UUID.randomUUID());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> controller().myApplications("not-a-status", 0, 20, AUTH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stats_delegatesToServiceWithExtractedUserId() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        var stats = new ApplicationStatsResponse(5, 2, 1, 1, 1);
        when(applicationService.getStats(userId)).thenReturn(stats);

        var response = controller().stats(AUTH);

        assertThat(response.getBody()).isEqualTo(stats);
    }

    @Test
    void check_wrapsBooleanInAppliedKey() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        when(applicationService.hasApplied(userId, jobId)).thenReturn(true);

        var response = controller().check(jobId, AUTH);

        assertThat(response.getBody()).isEqualTo(java.util.Map.of("applied", true));
    }
}
