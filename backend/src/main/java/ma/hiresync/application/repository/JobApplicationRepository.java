package ma.hiresync.application.repository;

import ma.hiresync.application.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    List<JobApplication> findByUserIdOrderByAppliedAtDesc(UUID userId);

    boolean existsByUserIdAndJobId(UUID userId, UUID jobId);

    Optional<JobApplication> findByUserIdAndJobId(UUID userId, UUID jobId);

    Optional<JobApplication> findByIdAndUserId(UUID id, UUID userId);
}
