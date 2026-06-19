package ma.hiresync.cv.repository;

import ma.hiresync.cv.entity.CvOptimization;
import ma.hiresync.cv.entity.CvOptimization.OptimizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvOptimizationRepository extends JpaRepository<CvOptimization, UUID> {

    List<CvOptimization> findByCvUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Server-side paginated + filtered optimization history.
     * Optional status filter and optional case-insensitive search on job title / company.
     * {@code q} must be lower-cased by the caller.
     */
    @Query("""
            SELECT o FROM CvOptimization o
            WHERE o.cv.user.id = :userId
              AND (:status IS NULL OR o.status = :status)
              AND (CAST(:q AS string) IS NULL
                   OR LOWER(o.jobTitle) LIKE CONCAT('%', CAST(:q AS string), '%')
                   OR LOWER(o.company)  LIKE CONCAT('%', CAST(:q AS string), '%'))
            ORDER BY o.createdAt DESC
            """)
    Page<CvOptimization> search(@Param("userId") UUID userId,
                                @Param("status") OptimizationStatus status,
                                @Param("q") String q,
                                Pageable pageable);

    long countByCvUserId(UUID userId);

    long countByCvUserIdAndStatus(UUID userId, OptimizationStatus status);

    @Query("""
            SELECT COALESCE(AVG(o.optimizedScore - o.originalScore), 0)
            FROM CvOptimization o
            WHERE o.cv.user.id = :userId AND o.status = :status
            """)
    double avgGain(@Param("userId") UUID userId, @Param("status") OptimizationStatus status);

    @Query("""
            SELECT COALESCE(MAX(o.optimizedScore), 0)
            FROM CvOptimization o
            WHERE o.cv.user.id = :userId AND o.status = :status
            """)
    int bestScore(@Param("userId") UUID userId, @Param("status") OptimizationStatus status);

    Optional<CvOptimization> findByIdAndCvUserId(UUID id, UUID userId);

    /**
     * Most recent optimization a user has for a given job whose status is NOT the
     * excluded one. Used to enforce "optimize a job only once" — we pass FAILED as
     * the excluded status so a technical failure can still be retried.
     */
    Optional<CvOptimization> findFirstByCvUserIdAndJobIdAndStatusNotOrderByCreatedAtDesc(
            UUID userId, String jobId, CvOptimization.OptimizationStatus status);
}
