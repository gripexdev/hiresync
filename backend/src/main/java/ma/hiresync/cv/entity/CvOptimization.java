package ma.hiresync.cv.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cv_optimizations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CvOptimization {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_id", nullable = false)
    private Cv cv;

    /** The job offer the CV is being optimized for */
    @Column(nullable = false)
    private String jobId;

    @Column(nullable = false)
    private String jobTitle;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OptimizationStatus status = OptimizationStatus.QUEUED;

    private int originalScore;
    private int optimizedScore;

    // ── Pre-flight compatibility check (CV ↔ job) ────────────────────────────
    /** 0–100 fit between the candidate's profile and the target job. */
    private int compatibilityScore;

    /** Why the optimization was rejected (only set when status = REJECTED). */
    @Column(columnDefinition = "TEXT")
    private String rejectionReason;

    /** Profession detected from the CV (e.g. "Développeur logiciel"). */
    private String candidateProfile;

    /** Profession the target job belongs to (e.g. "Assistant commercial"). */
    private String targetProfile;

    // ── ATS keyword analysis (CV ↔ job description) ──────────────────────────
    /** JSON array of job keywords found in the optimized CV. */
    @Column(columnDefinition = "TEXT")
    private String matchedKeywordsJson;

    /** JSON array of job keywords still missing after optimization. */
    @Column(columnDefinition = "TEXT")
    private String missingKeywordsJson;

    /** Raw JSON array of SuggestedChange objects returned by the LLM */
    @Column(columnDefinition = "TEXT")
    private String suggestedChangesJson;

    /** Full structured optimized CV as JSON (name, summary, experience, skills…) */
    @Column(columnDefinition = "TEXT")
    private String optimizedCvJson;

    /** AI-generated cover letter / application email as JSON {subject, body}. Cached on first generation. */
    @Column(columnDefinition = "TEXT")
    private String coverLetterJson;

    /** Path to the optimized CV file on disk */
    private String optimizedCvPath;

    /** Which LLM model was actually used */
    @Builder.Default
    private String modelUsed = "mistralai/mistral-7b-instruct:free";

    private Long processingTimeMs;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    public enum OptimizationStatus {
        QUEUED, PROCESSING, COMPLETED, FAILED,
        /** CV ↔ job are not a realistic match — optimization was stopped on purpose. */
        REJECTED
    }
}
