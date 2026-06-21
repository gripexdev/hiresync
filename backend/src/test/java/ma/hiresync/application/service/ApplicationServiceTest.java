package ma.hiresync.application.service;

import ma.hiresync.application.dto.ApplyRequest;
import ma.hiresync.application.entity.JobApplication;
import ma.hiresync.application.entity.JobApplication.ApplicationStatus;
import ma.hiresync.application.repository.JobApplicationRepository;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import ma.hiresync.cv.service.AtsScorer;
import ma.hiresync.job.entity.Job;
import ma.hiresync.job.repository.JobRepository;
import ma.hiresync.notification.NotificationService;
import ma.hiresync.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private JobApplicationRepository appRepo;
    @Mock private JobRepository jobRepo;
    @Mock private CvRepository cvRepo;
    @Mock private CvOptimizationRepository optimRepo;
    @Mock private AtsScorer atsScorer;
    @Mock private NotificationService notificationSvc;

    private ApplicationService service() {
        return new ApplicationService(appRepo, jobRepo, cvRepo, optimRepo, atsScorer, notificationSvc);
    }

    private Job job(UUID id) {
        return Job.builder().id(id).title("Développeur Backend").company("Acme")
                .location("Casablanca").requirements(List.of("Java", "Spring")).build();
    }

    private Cv cv(UUID id, UUID userId) {
        return Cv.builder().id(id).fileName("cv.pdf").atsScore(55)
                .extractedText("Expérience en Java et Spring").build();
    }

    @Test
    void apply_newApplication_savesSnapshotAndComputesMatchScore() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        Job job = job(jobId);
        Cv cv = cv(cvId, userId);

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv));
        when(appRepo.existsByUserIdAndJobId(userId, jobId)).thenReturn(false);
        when(optimRepo.findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
                any(), anyString(), any())).thenReturn(Optional.empty());
        when(atsScorer.computeJobMatch(cv.getExtractedText(), job.getRequirements()))
                .thenReturn(new AtsScorer.JobMatch(78, 100, List.of("Java", "Spring"), List.of()));

        var response = service().apply(userId, jobId, new ApplyRequest(cvId, "Motivé par ce poste"));

        assertThat(response.status()).isEqualTo("applied");
        assertThat(response.matchScore()).isEqualTo(78);
        assertThat(response.coverNote()).isEqualTo("Motivé par ce poste");

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(appRepo).save(saved.capture());
        assertThat(saved.getValue().getJobTitle()).isEqualTo("Développeur Backend");
        assertThat(saved.getValue().getCvFileName()).isEqualTo("cv.pdf");
    }

    @Test
    void apply_alreadyApplied_throwsAndNeverSaves() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job(jobId)));
        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv(cvId, userId)));
        when(appRepo.existsByUserIdAndJobId(userId, jobId)).thenReturn(true);

        assertThatThrownBy(() -> service().apply(userId, jobId, new ApplyRequest(cvId, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà postulé");

        verify(appRepo, never()).save(any());
    }

    @Test
    void apply_unknownJob_throwsBeforeTouchingCvOrRepo() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(jobRepo.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().apply(userId, jobId, new ApplyRequest(UUID.randomUUID(), null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("introuvable");

        verify(cvRepo, never()).findByIdAndUserId(any(), any());
        verify(appRepo, never()).save(any());
    }

    @Test
    void apply_prefersOptimizationScoreOverComputedJobMatch() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();

        when(jobRepo.findById(jobId)).thenReturn(Optional.of(job(jobId)));
        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv(cvId, userId)));
        when(appRepo.existsByUserIdAndJobId(userId, jobId)).thenReturn(false);

        var optimization = mock(CvOptimization.class);
        when(optimization.getOptimizedScore()).thenReturn(91);
        when(optimRepo.findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
                any(), anyString(), any())).thenReturn(Optional.of(optimization));

        var response = service().apply(userId, jobId, new ApplyRequest(cvId, null));

        assertThat(response.matchScore()).isEqualTo(91);
        verify(atsScorer, never()).computeJobMatch(any(), any());
    }

    @Test
    void markApplied_alreadyExists_returnsExistingWithoutDuplicateSave() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        var existing = JobApplication.builder()
                .id(UUID.randomUUID()).userId(userId).jobId(jobId)
                .jobTitle("Déjà postulé").status(ApplicationStatus.APPLIED).build();

        when(appRepo.findByUserIdAndJobId(userId, jobId)).thenReturn(Optional.of(existing));

        var response = service().markApplied(userId, jobId, UUID.randomUUID());

        assertThat(response.jobTitle()).isEqualTo("Déjà postulé");
        verify(appRepo, never()).save(any());
        verify(jobRepo, never()).findById(any());
    }

    @Test
    void updateStatus_validTransition_persistsAndNotifies() {
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        var application = JobApplication.builder()
                .id(appId).userId(userId).jobId(UUID.randomUUID())
                .jobTitle("Backend Dev").company("Acme")
                .status(ApplicationStatus.APPLIED).build();

        when(appRepo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(application));

        var response = service().updateStatus(userId, appId, "interview");

        assertThat(response.status()).isEqualTo("interview");
        verify(notificationSvc).create(eq(userId), eq(NotificationType.INTERVIEW_SCHEDULED),
                eq("Entretien programmé"), anyString(), eq("/applications"));
    }

    @Test
    void updateStatus_sameStatus_doesNotSendANotification() {
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        var application = JobApplication.builder()
                .id(appId).userId(userId).jobId(UUID.randomUUID())
                .jobTitle("Backend Dev").status(ApplicationStatus.APPLIED).build();

        when(appRepo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(application));

        service().updateStatus(userId, appId, "applied");

        verify(notificationSvc, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_invalidStatusString_throwsIllegalArgument() {
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();
        var application = JobApplication.builder()
                .id(appId).userId(userId).jobId(UUID.randomUUID())
                .jobTitle("Backend Dev").status(ApplicationStatus.APPLIED).build();

        when(appRepo.findByIdAndUserId(appId, userId)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service().updateStatus(userId, appId, "not-a-real-status"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Statut invalide");
    }

    @Test
    void getStats_countsApplicationsByStatusBucket() {
        UUID userId = UUID.randomUUID();
        when(appRepo.findByUserIdOrderByAppliedAtDesc(userId)).thenReturn(List.of(
                JobApplication.builder().status(ApplicationStatus.APPLIED).build(),
                JobApplication.builder().status(ApplicationStatus.IN_REVIEW).build(),
                JobApplication.builder().status(ApplicationStatus.INTERVIEW).build(),
                JobApplication.builder().status(ApplicationStatus.OFFER).build(),
                JobApplication.builder().status(ApplicationStatus.REJECTED).build(),
                JobApplication.builder().status(ApplicationStatus.REJECTED).build()
        ));

        var stats = service().getStats(userId);

        assertThat(stats.total()).isEqualTo(6);
        assertThat(stats.pending()).isEqualTo(2);   // APPLIED + IN_REVIEW
        assertThat(stats.interviews()).isEqualTo(1);
        assertThat(stats.offers()).isEqualTo(1);
        assertThat(stats.rejected()).isEqualTo(2);
    }

    @Test
    void hasApplied_delegatesDirectlyToRepository() {
        UUID userId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        when(appRepo.existsByUserIdAndJobId(userId, jobId)).thenReturn(true);

        assertThat(service().hasApplied(userId, jobId)).isTrue();
    }
}
