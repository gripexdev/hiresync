package ma.hiresync.application.repository;

import ma.hiresync.application.entity.JobApplication;
import ma.hiresync.application.entity.JobApplication.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByAppliedAtDesc(UUID userId);

    /** Server-side paginated list of all of a user's applications (table view). */
    Page<JobApplication> findByUserId(UUID userId, Pageable pageable);

    /** Server-side paginated list filtered by status (kanban column lazy-load). */
    Page<JobApplication> findByUserIdAndStatus(UUID userId, ApplicationStatus status, Pageable pageable);

    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    Optional<JobApplication> findByUserIdAndJobId(UUID userId, UUID jobId);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);
}
