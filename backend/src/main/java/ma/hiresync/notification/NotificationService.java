package ma.hiresync.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Pushes WebSocket notifications to specific users via STOMP.
 *
 * Angular subscribes to: /user/topic/cv-optimization
 * Spring sends to:        /user/{userId}/topic/cv-optimization
 *                         (SimpMessagingTemplate handles the routing)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messaging;

    /**
     * Push CV optimization event to the user who triggered it.
     *
     * @param userId         recipient user ID (used as WebSocket session principal)
     * @param optimizationId the optimization job ID
     * @param status         "completed" | "failed" | "processing"
     * @param message        optional human-readable message
     */
    public void pushCvOptimizationEvent(UUID userId, UUID optimizationId, String status, String message) {
        var payload = Map.of(
            "optimizationId", optimizationId.toString(),
            "status",         status,
            "message",        message != null ? message : ""
        );
        log.info("WebSocket push → user:{} optimization:{} status:{}", userId, optimizationId, status);
        // SimpMessagingTemplate converts userId.toString() into the STOMP user destination
        messaging.convertAndSendToUser(
            userId.toString(),
            "/topic/cv-optimization",
            payload
        );
    }
}
