package ma.hiresync.cv.entity;

import jakarta.persistence.*;
import lombok.*;
import ma.hiresync.auth.entity.User;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cvs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cv {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String fileName;

    /** Path on disk relative to upload-dir */
    @Column(nullable = false)
    private String filePath;

    private Long fileSize;
    private String mimeType;

    /** ATS score (0–100) computed at upload via PDFBox + keyword analysis */
    @Column(nullable = false)
    @Builder.Default
    private int atsScore = 0;

    /** Extracted raw text (stored for re-analysis without re-reading the file) */
    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private Instant uploadedAt = Instant.now();
}
