package ma.hiresync.job;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
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

    @Column(length = 512)
    String logoUrl;

    @Column(unique = true, nullable = false, length = 1024)
    String sourceUrl;

    String source;

    LocalDateTime postedAt;

    @Column(nullable = false)
    LocalDateTime scrapedAt;
}
