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

    /** Raw JSON array of SuggestedChange objects returned by the LLM */
    @Column(columnDefinition = "TEXT")
    private String suggestedChangesJson;

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
        QUEUED, PROCESSING, COMPLETED, FAILED
    }
}
