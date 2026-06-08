package ma.hiresync.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    boolean existsBySourceUrl(String sourceUrl);

    /** Jobs that haven't had their detail page fetched yet, newest first */
    List<Job> findTop20ByEnrichedFalseOrderByScrapedAtDesc();

    long countByEnrichedTrue();

    @Query("""
        SELECT j FROM Job j
        WHERE :q IS NULL OR :q = ''
           OR LOWER(j.title)    LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(j.company)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(j.location) LIKE LOWER(CONCAT('%', :q, '%'))
        """)
    Page<Job> search(@Param("q") String q, Pageable pageable);
}
