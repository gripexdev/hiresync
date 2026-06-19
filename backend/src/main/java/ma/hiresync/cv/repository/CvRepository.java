package ma.hiresync.cv.repository;

import ma.hiresync.cv.entity.Cv;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CvRepository extends JpaRepository<Cv, UUID> {

    List<Cv> findByUserIdOrderByUploadedAtDesc(UUID userId);

    /** Server-side paginated list, active CV pinned first, then most-recent uploads. */
    Page<Cv> findByUserIdOrderByActiveDescUploadedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    Optional<Cv> findFirstByUserIdOrderByUploadedAtDesc(UUID userId);

    Optional<Cv> findByIdAndUserId(UUID id, UUID userId);

    Optional<Cv> findByUserIdAndActiveTrue(UUID userId);

    @Modifying
    @Transactional
    @Query("UPDATE Cv c SET c.active = false WHERE c.user.id = :userId")
    void deactivateAll(UUID userId);
}
