package ma.hiresync.job;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "jobs", indexes = {
    @Index(name = "idx_jobs_source_url", columnList = "sourceUrl", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    String title;

    String company;
    String location;
    String contractType;
    String sector;
    String experienceLevel;

    @Column(columnDefinition = "TEXT")
    String description;

    /** Skills / personality traits scraped from the job detail page */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_requirements",
                     joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "requirement")
    @Builder.Default
    List<String> requirements = new ArrayList<>();

    @Column(length = 512)
    String logoUrl;

    @Column(unique = true, nullable = false, length = 1024)
    String sourceUrl;

    String source;

    LocalDateTime postedAt;

    @Column(nullable = false)
    LocalDateTime scrapedAt;

    /** True once the detail page has been fetched and description/requirements filled in */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    boolean enriched = false;
}
