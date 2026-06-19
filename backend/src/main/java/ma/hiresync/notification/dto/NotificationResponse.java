package ma.hiresync.notification.dto;

import ma.hiresync.notification.entity.Notification;
import ma.hiresync.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

/**
 * What the Angular client receives. Field names + the lower-cased {@code type}
 * match the frontend {@code Notification} model exactly.
 */
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        String link,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getLink(), n.isRead(), n.getCreatedAt());
    }
}
