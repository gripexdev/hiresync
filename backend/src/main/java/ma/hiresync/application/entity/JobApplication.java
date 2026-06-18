package ma.hiresync.application.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's application to a job. Job/CV details are snapshotted at apply time
 * (denormalized) so the record stays meaningful even if the source job is
 * re-scraped or the CV is deleted.
 *
 * A user can only apply once per job — enforced by the unique constraint.
 */
@Entity
@Table(name = "job_applications",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_application_user_job", columnNames = {"user_id", "job_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // ── Job snapshot ──────────────────────────────────────────────────────────
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String jobTitle;

    private String company;
    private String location;

    // ── CV snapshot ───────────────────────────────────────────────────────────
    @Column(name = "cv_id", nullable = false)
    private UUID cvId;

    private String cvFileName;

    // ── Application state ──────────────────────────────────────────────────────
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(columnDefinition = "TEXT")
    private String coverNote;

    private Integer matchScore;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant appliedAt = Instant.now();

    private Instant updatedAt;

    public enum ApplicationStatus {
        APPLIED, IN_REVIEW, INTERVIEW, OFFER, REJECTED
    }
}
