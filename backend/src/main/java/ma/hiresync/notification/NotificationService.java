package ma.hiresync.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
     * @param status         "completed" | "failed" | "processing" | "rejected"
     * @param message        optional human-readable message
     */
    public void pushCvOptimizationEvent(UUID userId, UUID optimizationId, String status, String message) {
        pushCvOptimizationEvent(userId, optimizationId, status, message, null);
    }

    /**
     * Push CV optimization event including the actual AI provider that ran.
     * The provider field is included in the payload so Angular can display which
     * model was used without waiting for a separate REST fetch.
     *
     * @param provider actual provider name (e.g. "Groq Llama 3.3 70B"), or null
     */
    public void pushCvOptimizationEvent(UUID userId, UUID optimizationId, String status,
                                         String message, String provider) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("optimizationId", optimizationId.toString());
        payload.put("status",         status);
        payload.put("message",        message != null ? message : "");
        if (provider != null && !provider.isBlank()) {
            payload.put("provider", provider);
        }

        log.info("WebSocket push → user:{} optimization:{} status:{} provider:{}",
            userId, optimizationId, status, provider != null ? provider : "—");

        messaging.convertAndSendToUser(
            userId.toString(),
            "/topic/cv-optimization",
            payload
        );
    }
}
