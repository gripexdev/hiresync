package ma.hiresync.cv.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OptimizeRequest(
        @NotNull  UUID   cvId,
        @NotBlank String jobId,
        @NotBlank String jobTitle,
        @NotBlank String company,
        @NotBlank String jobDescription
) {}
