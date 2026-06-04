package ma.hiresync.cv.dto;

import java.util.UUID;

public record OptimizeTriggerResponse(
        UUID   optimizationId,
        String status,
        String message
) {}
