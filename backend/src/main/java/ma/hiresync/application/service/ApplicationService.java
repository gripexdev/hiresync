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
import ma.hiresync.cv.repository.CvRepository;
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
            .matchScore(cv.getAtsScore() > 0 ? cv.getAtsScore() : null)
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
            .matchScore(cv.getAtsScore() > 0 ? cv.getAtsScore() : null)
            .appliedAt(Instant.now())
            .build();
        appRepo.save(application);
        log.info("User {} marked as applied to job {} ({})", userId, jobId, job.getTitle());
        return ApplicationResponse.from(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getMyApplications(UUID userId) {
        return appRepo.findByUserIdOrderByAppliedAtDesc(userId)
                .stream().map(ApplicationResponse::from).toList();
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
