package ma.hiresync.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.hiresync.notification.dto.NotificationResponse;
import ma.hiresync.notification.entity.Notification;
import ma.hiresync.notification.entity.NotificationType;
import ma.hiresync.notification.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Two responsibilities:
 *  1. Persist per-user notifications raised by real product events
 *     (optimization done/failed, application status change) and serve the
 *     in-app bell / list via REST.
 *  2. Push the live, transient WebSocket event the CV-optimizer page listens to.
 *
 * WebSocket routing:
 *   Angular subscribes to: /user/topic/cv-optimization
 *   Spring sends to:        /user/{userId}/topic/cv-optimization
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate  messaging;
    private final NotificationRepository repo;

    // ── Persisted notifications (the bell / notifications page) ──────────────────

    /**
     * Create and store a notification for a user. Returns the saved row so callers
     * can log / chain. Safe to call inside an existing transaction.
     */
    @Transactional
    public Notification create(UUID userId, NotificationType type, String title, String message, String link) {
        Notification saved = repo.save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .link(link)
                .build());
        log.info("Notification created → user:{} type:{} \"{}\"", userId, type, title);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID userId, Pageable pageable) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repo.countByUserIdAndReadFalse(userId);
    }

    /** Mark a single notification read. No-op if it isn't the user's or is already read. */
    @Transactional
    public void markRead(UUID id, UUID userId) {
        repo.findByIdAndUserId(id, userId).ifPresent(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                repo.save(n);
            }
        });
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repo.markAllReadForUser(userId);
    }

    // ── Live WebSocket event for the CV-optimizer page (transient, not stored) ───

    public void pushCvOptimizationEvent(UUID userId, UUID optimizationId, String status, String message) {
        pushCvOptimizationEvent(userId, optimizationId, status, message, null);
    }

    /**
     * Push CV optimization event including the actual AI provider that ran, so the
     * optimizer page can show which model was used without a second REST fetch.
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
