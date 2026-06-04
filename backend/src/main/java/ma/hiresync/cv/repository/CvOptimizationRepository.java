package ma.hiresync.cv.repository;

import ma.hiresync.cv.entity.CvOptimization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvOptimizationRepository extends JpaRepository<CvOptimization, UUID> {

    List<CvOptimization> findByCvUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<CvOptimization> findByIdAndCvUserId(UUID id, UUID userId);
}
