package ma.hiresync.job.repository;

import ma.hiresync.job.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    boolean existsBySourceUrl(String sourceUrl);

    /** Jobs that haven't had their detail page fetched yet, newest first */
    List<Job> findTop20ByEnrichedFalseOrderByScrapedAtDesc();

    long countByEnrichedTrue();

    // ── Facet counts: raw value → job count, bucketed in the service ──────────
    @Query("SELECT j.experienceLevel, COUNT(j) FROM Job j GROUP BY j.experienceLevel")
    List<Object[]> countByExperience();

    @Query("SELECT j.contractType, COUNT(j) FROM Job j GROUP BY j.contractType")
    List<Object[]> countByContractType();

    @Query("SELECT j.sector, COUNT(j) FROM Job j GROUP BY j.sector")
    List<Object[]> countBySector();

    @Query("SELECT j.location, COUNT(j) FROM Job j GROUP BY j.location")
    List<Object[]> countByLocation();
}
