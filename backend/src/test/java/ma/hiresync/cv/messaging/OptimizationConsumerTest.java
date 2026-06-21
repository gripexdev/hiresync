package ma.hiresync.cv.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.hiresync.cv.dto.CompatibilityVerdict;
import ma.hiresync.cv.entity.Cv;
import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.entity.CvOptimization.OptimizationStatus;
import ma.hiresync.cv.repository.CvOptimizationRepository;
import ma.hiresync.cv.repository.CvRepository;
import ma.hiresync.cv.service.AiGatewayService;
import ma.hiresync.cv.service.AiGatewayService.AiResult;
import ma.hiresync.cv.service.AtsScorer;
import ma.hiresync.notification.NotificationService;
import ma.hiresync.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * OptimizationConsumer orchestrates the entire CV-optimization business flow
 * (compatibility gate → AI rewrite → job-aware scoring → persistence →
 * notifications). It's the single most important piece of async logic in the
 * platform, which makes it worth the heavier mocking cost of testing directly.
 */
@ExtendWith(MockitoExtension.class)
class OptimizationConsumerTest {

    @Mock private CvOptimizationRepository optimRepo;
    @Mock private CvRepository cvRepo;
    @Mock private AiGatewayService aiGateway;
    @Mock private AtsScorer atsScorer;
    @Mock private NotificationService notificationSvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OptimizationConsumer consumer() {
        return new OptimizationConsumer(optimRepo, cvRepo, aiGateway, atsScorer, notificationSvc, objectMapper);
    }

    private Cv cvWithText(UUID id, String text) {
        return Cv.builder().id(id).extractedText(text).build();
    }

    @Test
    void consume_optimizationNotFound_returnsEarlyWithoutTouchingAnything() {
        UUID optimId = UUID.randomUUID();
        when(optimRepo.findById(optimId)).thenReturn(Optional.empty());

        consumer().consume(new OptimizationMessage(optimId, UUID.randomUUID(), UUID.randomUUID(), "desc"));

        verifyNoInteractions(aiGateway, atsScorer, notificationSvc);
    }

    @Test
    void consume_cvNotFound_returnsEarlyWithoutCallingAiGateway() {
        UUID optimId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        when(optimRepo.findById(optimId)).thenReturn(Optional.of(CvOptimization.builder().id(optimId).build()));
        when(cvRepo.findById(cvId)).thenReturn(Optional.empty());

        consumer().consume(new OptimizationMessage(optimId, cvId, UUID.randomUUID(), "desc"));

        verifyNoInteractions(aiGateway);
    }

    @Test
    void consume_incompatibleProfile_rejectsWithoutCallingOptimizeCv() {
        UUID optimId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).jobTitle("Directeur Général").build();
        Cv cv = cvWithText(cvId, "Stagiaire développeur junior, 0 an d'expérience");

        when(optimRepo.findById(optimId)).thenReturn(Optional.of(optim));
        when(cvRepo.findById(cvId)).thenReturn(Optional.of(cv));
        when(aiGateway.assessCompatibility(anyString(), anyString(), anyString())).thenReturn(
                new CompatibilityVerdict(false, 12, "Profil junior incompatible avec un poste de direction.",
                        "Développeur junior", "Directeur Général", List.of(), List.of("Leadership", "10 ans d'expérience")));

        consumer().consume(new OptimizationMessage(optimId, cvId, userId, "Poste de direction générale"));

        assertThat(optim.getStatus()).isEqualTo(OptimizationStatus.REJECTED);
        assertThat(optim.getRejectionReason()).contains("incompatible");
        assertThat(optim.getCompletedAt()).isNotNull();
        verify(aiGateway, never()).optimizeCv(anyString(), anyString());
        verify(notificationSvc).create(eq(userId), eq(NotificationType.CV_REJECTED), anyString(), anyString(), anyString());
        // Saved once for the PROCESSING transition, once more for the REJECTED outcome.
        verify(optimRepo, times(2)).save(optim);
    }

    @Test
    void consume_compatibleProfile_completesWithJobAwareScoring() {
        UUID optimId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).jobTitle("Développeur Backend").build();
        Cv cv = cvWithText(cvId, "Développeur Java avec 2 ans d'expérience");

        when(optimRepo.findById(optimId)).thenReturn(Optional.of(optim));
        when(cvRepo.findById(cvId)).thenReturn(Optional.of(cv));
        when(aiGateway.assessCompatibility(anyString(), anyString(), anyString())).thenReturn(
                new CompatibilityVerdict(true, 82, "Profil compatible.", "Développeur", "Développeur Backend",
                        List.of("Java"), List.of()));

        String llmJson = """
                {"atsKeywords": ["Java", "Spring", "Docker"],
                 "optimizedCv": {"summary": "Développeur Java Spring Docker confirmé"},
                 "suggestions": [{"type": "keyword_added", "description": "Ajout de Docker"}]}
                """;
        when(aiGateway.optimizeCv(anyString(), anyString())).thenReturn(new AiResult(llmJson, "Groq Llama 3.3 70B"));

        // First call scores the original CV text, second call scores the optimized text.
        when(atsScorer.computeJobMatch(anyString(), eq(List.of("Java", "Spring", "Docker"))))
                .thenReturn(new AtsScorer.JobMatch(61, 33, List.of("Java"), List.of("Spring", "Docker")))
                .thenReturn(new AtsScorer.JobMatch(92, 100, List.of("Java", "Spring", "Docker"), List.of()));

        consumer().consume(new OptimizationMessage(optimId, cvId, userId, "Poste backend"));

        assertThat(optim.getStatus()).isEqualTo(OptimizationStatus.COMPLETED);
        assertThat(optim.getOriginalScore()).isEqualTo(61);
        assertThat(optim.getOptimizedScore()).isEqualTo(92);
        assertThat(optim.getModelUsed()).isEqualTo("Groq Llama 3.3 70B");
        assertThat(optim.getOptimizedCvJson()).contains("Développeur Java Spring Docker confirmé");

        verify(notificationSvc).pushCvOptimizationEvent(eq(userId), eq(optimId), eq("completed"), anyString(), eq("Groq Llama 3.3 70B"));
        verify(notificationSvc).create(eq(userId), eq(NotificationType.CV_OPTIMIZED), anyString(), anyString(), anyString());
    }

    @Test
    void consume_normalizesUnknownSuggestionTypesToCanonicalBucket() throws Exception {
        UUID optimId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).jobTitle("Dev").build();
        Cv cv = cvWithText(cvId, "CV texte");

        when(optimRepo.findById(optimId)).thenReturn(Optional.of(optim));
        when(cvRepo.findById(cvId)).thenReturn(Optional.of(cv));
        when(aiGateway.assessCompatibility(anyString(), anyString(), anyString()))
                .thenReturn(new CompatibilityVerdict(true, 80, "ok", "Dev", "Dev", List.of(), List.of()));

        String llmJson = """
                {"atsKeywords": [],
                 "optimizedCv": {},
                 "suggestions": [{"type": "contact_info_added", "description": "x"},
                                  {"type": "summary_rewritten", "description": "y"}]}
                """;
        when(aiGateway.optimizeCv(anyString(), anyString())).thenReturn(new AiResult(llmJson, "Gemini 2.0 Flash"));
        when(atsScorer.computeJobMatch(any(), any())).thenReturn(new AtsScorer.JobMatch(50, 0, List.of(), List.of()));

        consumer().consume(new OptimizationMessage(optimId, cvId, userId, "desc"));

        var suggestions = objectMapper.readTree(optim.getSuggestedChangesJson());
        assertThat(suggestions.get(0).get("type").asText()).isEqualTo("format_improved"); // contact → format bucket
        assertThat(suggestions.get(1).get("type").asText()).isEqualTo("section_rewritten"); // summary → section bucket
    }

    @Test
    void consume_aiGatewayThrows_marksFailedAndNotifies() {
        UUID optimId = UUID.randomUUID();
        UUID cvId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var optim = CvOptimization.builder().id(optimId).jobTitle("Dev").build();
        Cv cv = cvWithText(cvId, "CV texte");

        when(optimRepo.findById(optimId)).thenReturn(Optional.of(optim));
        when(cvRepo.findById(cvId)).thenReturn(Optional.of(cv));
        when(aiGateway.assessCompatibility(anyString(), anyString(), anyString()))
                .thenReturn(new CompatibilityVerdict(true, 80, "ok", "Dev", "Dev", List.of(), List.of()));
        when(aiGateway.optimizeCv(anyString(), anyString()))
                .thenThrow(new RuntimeException("All AI providers failed"));

        consumer().consume(new OptimizationMessage(optimId, cvId, userId, "desc"));

        assertThat(optim.getStatus()).isEqualTo(OptimizationStatus.FAILED);
        verify(notificationSvc).create(eq(userId), eq(NotificationType.CV_FAILED), anyString(), anyString(), anyString());
        verify(notificationSvc).pushCvOptimizationEvent(eq(userId), eq(optimId), eq("failed"), anyString());
    }
}
