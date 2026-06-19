package ma.hiresync.notification;

import lombok.RequiredArgsConstructor;
import ma.hiresync.auth.service.JwtService;
import ma.hiresync.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * In-app notifications for the signed-in user. Server-side paginated, matching
 * the rest of the app (Spring {@code Page} envelope).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtService          jwtService;

    /** GET /api/notifications?page=&size= — most recent first. */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("Authorization") String authHeader) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.list(extractUserId(authHeader), pageable));
    }

    /** GET /api/notifications/unread-count — drives the topbar bell badge. */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(
            @RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(extractUserId(authHeader))));
    }

    /** POST /api/notifications/{id}/read — mark one as read. */
    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String authHeader) {
        notificationService.markRead(id, extractUserId(authHeader));
        return ResponseEntity.noContent().build();
    }

    /** POST /api/notifications/read-all — mark every unread one as read. */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllRead(
            @RequestHeader("Authorization") String authHeader) {
        int updated = notificationService.markAllRead(extractUserId(authHeader));
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtService.extractUserId(token);
    }
}
