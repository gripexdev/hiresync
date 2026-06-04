package ma.hiresync.cv.dto;

import ma.hiresync.cv.entity.Cv;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CvResponse(
        UUID    id,
        String  fileName,
        Long    fileSize,
        String  mimeType,
        Instant uploadedAt,
        int     atsScore,
        boolean active,
        List<SectionDto> parsedSections
) {
    public record SectionDto(String title, String content) {}

    public static CvResponse from(Cv cv, Map<String, String> sections) {
        return new CvResponse(
            cv.getId(), cv.getFileName(), cv.getFileSize(),
            cv.getMimeType(), cv.getUploadedAt(), cv.getAtsScore(), cv.isActive(),
            sections.entrySet().stream()
                    .map(e -> new SectionDto(e.getKey(), e.getValue()))
                    .toList()
        );
    }

    public static CvResponse from(Cv cv) {
        return from(cv, Map.of());
    }
}
