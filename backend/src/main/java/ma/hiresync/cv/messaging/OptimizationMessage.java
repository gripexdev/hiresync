package ma.hiresync.cv.messaging;

import java.io.Serializable;
import java.util.UUID;

/** Message sent to RabbitMQ cv.optimize.queue */
public record OptimizationMessage(
        UUID   optimizationId,
        UUID   cvId,
        UUID   userId,
        String jobDescription
) implements Serializable {}
