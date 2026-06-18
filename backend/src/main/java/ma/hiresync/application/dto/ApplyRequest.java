package ma.hiresync.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of POST /api/applications/{jobId}. */
public record ApplyRequest(
        @NotNull(message = "Veuillez sélectionner un CV") UUID cvId,
        String coverNote
) {}
