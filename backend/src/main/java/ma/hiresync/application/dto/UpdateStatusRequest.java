package ma.hiresync.application.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of PATCH /api/applications/{id}/status. */
public record UpdateStatusRequest(
        @NotBlank(message = "Le statut est requis") String status
) {}
