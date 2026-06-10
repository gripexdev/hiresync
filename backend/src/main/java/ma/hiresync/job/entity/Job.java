package ma.hiresync.job.entity;

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
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    private String company;
    private String location;
    private String contractType;
    private String sector;
    private String experienceLevel;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Skills / personality traits scraped from the job detail page */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "job_requirements",
                     joinColumns = @JoinColumn(name = "job_id"))
    @Column(name = "requirement")
    @Builder.Default
    private List<String> requirements = new ArrayList<>();

    @Column(length = 512)
    private String logoUrl;

    @Column(unique = true, nullable = false, length = 1024)
    private String sourceUrl;

    private String source;

    private LocalDateTime postedAt;

    @Column(nullable = false)
    private LocalDateTime scrapedAt;

    /** True once the detail page has been fetched and description/requirements filled in */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private boolean enriched = false;
}
