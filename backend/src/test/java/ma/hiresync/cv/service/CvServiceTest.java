package ma.hiresync.cv.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.hiresync.auth.repository.UserRepository;
import ma.hiresync.cv.dto.OptimizeRequest;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.entity.CvOptimization.OptimizationStatus;
import ma.hiresync.cv.messaging.OptimizationProducer;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import ma.hiresync.job.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Only the dependency-light, deterministic parts of CvService are unit-tested
 * here (activate/delete/dedup/keyword-boost JSON manipulation). upload() and
 * PDF/cover-letter generation depend on real file I/O or further heavy
 * collaborators and are better suited to integration tests.
 */
@ExtendWith(MockitoExtension.class)
class CvServiceTest {

    @Mock private CvRepository cvRepo;
    @Mock private CvOptimizationRepository optimRepo;
    @Mock private UserRepository userRepo;
    @Mock private AtsScorer atsScorer;
    @Mock private OptimizationProducer producer;
    @Mock private CvPdfGenerator pdfGenerator;
    @Mock private CvTextParser cvTextParser;
    @Mock private JobRepository jobRepo;
    @Mock private AiGatewayService aiGateway;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CvService service() {
        return new CvService(cvRepo, optimRepo, userRepo, atsScorer, producer,
                pdfGenerator, cvTextParser, objectMapper, jobRepo, aiGateway);
    }

    @Test
    void activate_deactivatesAllThenActivatesTheChosenCv() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        Cv cv = Cv.builder().id(cvId).active(false).build();
        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv));

        service().activate(cvId, userId);

        verify(cvRepo).deactivateAll(userId);
        assertThat(cv.isActive()).isTrue();
        verify(cvRepo).save(cv);
    }

    @Test
    void activate_unknownCv_throwsWithoutTouchingDeactivateAll() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().activate(cvId, userId)).isInstanceOf(RuntimeException.class);

        verify(cvRepo, never()).deactivateAll(any());
    }

    @Test
    void delete_existingCv_removesItFromTheRepository() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        Cv cv = Cv.builder().id(cvId).filePath("/tmp/does-not-exist.pdf").build();
        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv));

        service().delete(cvId, userId);

        verify(cvRepo).delete(cv);
    }

    @Test
    void triggerOptimization_existingNonFailedOptimization_returnsItInsteadOfQueueingAnother() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        Cv cv = Cv.builder().id(cvId).atsScore(50).build();
        var req = new OptimizeRequest(cvId, "job-1", "Backend Dev", "Acme", "description");

        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv));
        var existing = CvOptimization.builder().id(UUID.randomUUID()).status(OptimizationStatus.COMPLETED).build();
        when(optimRepo.findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
                userId, "job-1", OptimizationStatus.FAILED)).thenReturn(Optional.of(existing));

        var response = service().triggerOptimization(req, userId);

        assertThat(response.alreadyOptimized()).isTrue();
        assertThat(response.optimizationId()).isEqualTo(existing.getId());
        verify(optimRepo, never()).save(any());
    }

    @Test
    void triggerOptimization_noExistingRun_createsAndQueuesANewOptimization() {
        UUID userId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        Cv cv = Cv.builder().id(cvId).atsScore(50).build();
        var req = new OptimizeRequest(cvId, "job-1", "Backend Dev", "Acme", "description");

        when(cvRepo.findByIdAndUserId(cvId, userId)).thenReturn(Optional.of(cv));
        when(optimRepo.findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
                userId, "job-1", OptimizationStatus.FAILED)).thenReturn(Optional.empty());

        // The real method registers a TransactionSynchronization (publish only
        // after commit) — outside a real @Transactional proxy there's no active
        // transaction, so we activate synchronization manually for this test and
        // fire the registered callbacks ourselves to simulate the commit.
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service().triggerOptimization(req, userId);

            assertThat(response.alreadyOptimized()).isFalse();
            assertThat(response.status()).isEqualTo("queued");
            verify(optimRepo).save(any(CvOptimization.class));
            verify(optimRepo).flush();

            for (var sync : org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(producer).send(any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void boostKeywords_addsNewKeywordsToSkillsWithoutDuplicates() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        String cvJson = "{\"skills\":[\"Java\",\"Spring\"],\"coreCompetencies\":[]}";
        var optim = CvOptimization.builder()
                .id(optimId).status(OptimizationStatus.COMPLETED)
                .cv(Cv.builder().id(UUID.randomUUID()).build())
                .optimizedCvJson(cvJson)
                .originalScore(60)
                .matchedKeywordsJson("[\"Java\"]")
                .missingKeywordsJson("[\"Docker\"]")
                .build();
        when(optimRepo.findByIdAndCvUserId(optimId, userId)).thenReturn(Optional.of(optim));
        when(atsScorer.computeJobMatch(any(), any()))
                .thenReturn(new AtsScorer.JobMatch(85, 100, List.of("Java", "Docker"), List.of()));

        service().boostKeywords(optimId, userId, List.of("Docker", "java"));

        String updatedJson = optim.getOptimizedCvJson();
        var skillsNode = objectMapper.readTree(updatedJson).get("skills");
        List<String> skills = objectMapper.convertValue(skillsNode, List.class);

        // "java" (lowercase) must not duplicate the existing "Java" entry.
        assertThat(skills).containsExactlyInAnyOrder("Java", "Spring", "Docker");
        assertThat(optim.getOptimizedScore()).isEqualTo(85);
    }

    @Test
    void boostKeywords_noKeywordsProvided_returnsUnchangedWithoutCallingAtsScorer() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).status(OptimizationStatus.COMPLETED)
                .cv(Cv.builder().id(UUID.randomUUID()).build())
                .optimizedCvJson("{\"skills\":[]}").build();
        when(optimRepo.findByIdAndCvUserId(optimId, userId)).thenReturn(Optional.of(optim));

        service().boostKeywords(optimId, userId, List.of());

        verify(atsScorer, never()).computeJobMatch(any(), any());
        verify(optimRepo, never()).save(any());
    }

    @Test
    void boostKeywords_optimizationNotCompleted_throws() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).status(OptimizationStatus.PROCESSING).build();
        when(optimRepo.findByIdAndCvUserId(optimId, userId)).thenReturn(Optional.of(optim));

        assertThatThrownBy(() -> service().boostKeywords(optimId, userId, List.of("Docker")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void boostKeywords_missingOptimizedCvJson_throwsDescriptiveError() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).status(OptimizationStatus.COMPLETED)
                .optimizedCvJson(null).build();
        when(optimRepo.findByIdAndCvUserId(optimId, userId)).thenReturn(Optional.of(optim));

        assertThatThrownBy(() -> service().boostKeywords(optimId, userId, List.of("Docker")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pre-date");
    }

    @Test
    void getHistoryStats_aggregatesCountsFromRepository() {
        UUID userId = UUID.randomUUID();
        when(optimRepo.countByCvUserId(userId)).thenReturn(10L);
        when(optimRepo.countByCvUserIdAndStatus(userId, OptimizationStatus.COMPLETED)).thenReturn(6L);
        when(optimRepo.countByCvUserIdAndStatus(userId, OptimizationStatus.REJECTED)).thenReturn(2L);
        when(optimRepo.countByCvUserIdAndStatus(userId, OptimizationStatus.FAILED)).thenReturn(2L);
        when(optimRepo.avgGain(userId, OptimizationStatus.COMPLETED)).thenReturn(21.4);
        when(optimRepo.bestScore(userId, OptimizationStatus.COMPLETED)).thenReturn(92);

        var stats = service().getHistoryStats(userId);

        assertThat(stats.total()).isEqualTo(10);
        assertThat(stats.completed()).isEqualTo(6);
        assertThat(stats.avgGain()).isEqualTo(21);
        assertThat(stats.bestScore()).isEqualTo(92);
    }
}
