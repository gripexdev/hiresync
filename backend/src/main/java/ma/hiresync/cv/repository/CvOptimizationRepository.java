package ma.hiresync.cv.repository;

import ma.hiresync.cv.entity.CvOptimization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvOptimizationRepository extends JpaRepository<CvOptimization, UUID> {

    List<CvOptimization> findByCvUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CvOptimization> findByIdAndCvUserId(UUID id, UUID userId);

    /**
     * Most recent optimization a user has for a given job whose status is NOT the
     * excluded one. Used to enforce "optimize a job only once" — we pass FAILED as
     * the excluded status so a technical failure can still be retried.
     */
    Optional<CvOptimization> findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
            UUID userId, String jobId, CvOptimization.OptimizationStatus status);
}
