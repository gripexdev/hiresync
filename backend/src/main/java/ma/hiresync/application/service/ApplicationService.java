package ma.hiresync.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.application.dto.ApplicationResponse;
import ma.hiresync.application.dto.ApplicationStatsResponse;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApplicationService {

    private final JobApplicationRepository appRepo;
    private final JobRepository            jobRepo;
    private final CvRepository             cvRepo;
    private final CvOptimizationRepository optimRepo;
    private final AtsScorer                atsScorer;

    /** Apply to a job with a chosen CV. Snapshots job + CV details at apply time. */
    public ApplicationResponse apply(UUID userId, UUID jobId, ApplyRequest req) {
        Job job = jobRepo.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Offre introuvable (not found)"));

        Cv cv = cvRepo.findByIdAndUserId(req.cvId(), userId)
            .orElseThrow(() -> new RuntimeException("CV introuvable (not found)"));

        if (appRepo.existsByUserIdAndJobId(userId, jobId)) {
            throw new IllegalStateException("Vous avez déjà postulé à cette offre.");
        }

        var application = JobApplication.builder()
            .userId(userId)
            .jobId(job.getId())
            .jobTitle(job.getTitle())
            .company(job.getCompany())
            .location(job.getLocation())
            .cvId(cv.getId())
            .cvFileName(cv.getFileName())
            .status(ApplicationStatus.APPLIED)
            .coverNote(req.coverNote() != null && !req.coverNote().isBlank() ? req.coverNote().trim() : null)
            .matchScore(computeMatchScore(userId, job, cv))
            .appliedAt(Instant.now())
            .build();

        appRepo.save(application);
        log.info("User {} applied to job {} ({}) with CV {}", userId, jobId, job.getTitle(), cv.getId());
        return ApplicationResponse.from(application);
    }

    /**
     * Record that the user went to apply on the company's site (clicked "Postuler").
     * Idempotent: if an application already exists for this job, it is returned
     * unchanged — clicking the apply link twice never errors.
     */
    public ApplicationResponse markApplied(UUID userId, UUID jobId, UUID cvId) {
        var existing = appRepo.findByUserIdAndJobId(userId, jobId);
        if (existing.isPresent()) return ApplicationResponse.from(existing.get());

        Job job = jobRepo.findById(jobId)
            .orElseThrow(() -> new RuntimeException("Offre introuvable (not found)"));
        Cv cv = cvRepo.findByIdAndUserId(cvId, userId)
            .orElseThrow(() -> new RuntimeException("CV introuvable (not found)"));

        var application = JobApplication.builder()
            .userId(userId)
            .jobId(job.getId())
            .jobTitle(job.getTitle())
            .company(job.getCompany())
            .location(job.getLocation())
            .cvId(cv.getId())
            .cvFileName(cv.getFileName())
            .status(ApplicationStatus.APPLIED)
            .matchScore(computeMatchScore(userId, job, cv))
            .appliedAt(Instant.now())
            .build();
        appRepo.save(application);
        log.info("User {} marked as applied to job {} ({})", userId, jobId, job.getTitle());
        return ApplicationResponse.from(application);
    }

    /** Update the status of one of the user's applications (kanban move / manual change). */
    public ApplicationResponse updateStatus(UUID userId, UUID applicationId, String statusRaw) {
        var application = appRepo.findByIdAndUserId(applicationId, userId)
            .orElseThrow(() -> new RuntimeException("Candidature introuvable (not found)"));

        ApplicationStatus newStatus;
        try {
            newStatus = ApplicationStatus.valueOf(statusRaw == null ? "" : statusRaw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut invalide : " + statusRaw);
        }

        application.setStatus(newStatus);
        application.setUpdatedAt(Instant.now());
        appRepo.save(application);
        log.info("User {} updated application {} → status {}", userId, applicationId, newStatus);
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getMyApplications(UUID userId) {
        return appRepo.findByUserIdOrderByAppliedAtDesc(userId)
                .stream()
                // Enrich with the live, job-specific match score so every card (incl. older
                // ones stored with the generic CV score) reflects the real CV↔job fit.
                .map(a -> ApplicationResponse.from(a, liveMatchScore(userId, a)))
                .toList();
    }

    /**
     * The real CV↔job compatibility score, preferring the optimization result for
     * this exact job; falls back to the value snapshotted at apply time.
     */
    private Integer liveMatchScore(UUID userId, JobApplication a) {
        Integer optimScore = optimizationScore(userId, a.getJobId());
        return optimScore != null ? optimScore : a.getMatchScore();
    }

    /**
     * Compute the match score at apply time:
     *   1. the optimization's optimized ATS score for this job (most accurate), else
     *   2. a job-specific ATS match of the CV text against the job's requirements, else
     *   3. the CV's generic upload ATS score.
     */
    private Integer computeMatchScore(UUID userId, Job job, Cv cv) {
        Integer optimScore = optimizationScore(userId, job.getId());
        if (optimScore != null) return optimScore;

        String cvText = cv.getExtractedText();
        if (cvText != null && !cvText.isBlank()
                && job.getRequirements() != null && !job.getRequirements().isEmpty()) {
            return atsScorer.computeJobMatch(cvText, job.getRequirements()).score();
        }
        return cv.getAtsScore() > 0 ? cv.getAtsScore() : null;
    }

    /** Optimized ATS score from the most recent non-failed optimization for this job, or null. */
    private Integer optimizationScore(UUID userId, UUID jobId) {
        return optimRepo.findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
                    userId, jobId.toString(), CvOptimization.OptimizationStatus.FAILED)
                .map(CvOptimization::getOptimizedScore)
                .filter(s -> s > 0)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasApplied(UUID userId, UUID jobId) {
        return appRepo.existsByUserIdAndJobId(userId, jobId);
    }

    @Transactional(readOnly = true)
    public ApplicationStatsResponse getStats(UUID userId) {
        var apps = appRepo.findByUserIdOrderByAppliedAtDesc(userId);
        long pending    = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.APPLIED
                                                 || a.getStatus() == ApplicationStatus.IN_REVIEW).count();
        long interviews = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW).count();
        long offers     = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.OFFER).count();
        long rejected   = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count();
        return new ApplicationStatsResponse(apps.size(), pending, interviews, offers, rejected);
    }
}
