package ma.hiresync.cv.dto;

import java.util.UUID;

public record OptimizeTriggerResponse(
        UUID    optimizationId,
        String  status,
        String  message,
        boolean alreadyOptimized   // true → returned an existing optimization, no new run started
) {}
