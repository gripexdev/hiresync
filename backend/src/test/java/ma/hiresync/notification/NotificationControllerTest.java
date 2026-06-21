package ma.hiresync.notification;

import ma.hiresync.auth.service.JwtService;
import ma.hiresync.notification.dto.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private JwtService jwtService;

    private NotificationController controller() {
        return new NotificationController(notificationService, jwtService);
    }

    private static final String AUTH = "Bearer some-jwt";

    @Test
    void list_extractsUserIdAndDelegatesPagination() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        Page<NotificationResponse> page = new PageImpl<>(List.of());
        when(notificationService.list(eq(userId), any())).thenReturn(page);

        var response = controller().list(0, 20, AUTH);

        assertThat(response.getBody()).isSameAs(page);
    }

    @Test
    void unreadCount_wrapsLongInCountKey() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        when(notificationService.unreadCount(userId)).thenReturn(4L);

        var response = controller().unreadCount(AUTH);

        assertThat(response.getBody()).isEqualTo(java.util.Map.of("count", 4L));
    }

    @Test
    void markRead_delegatesIdAndUserIdThenReturns204() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);

        var response = controller().markRead(notifId, AUTH);

        verify(notificationService).markRead(notifId, userId);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void markAllRead_wrapsUpdatedCountInResponse() {
        UUID userId = UUID.randomUUID();
        when(jwtService.extractUserId("some-jwt")).thenReturn(userId);
        when(notificationService.markAllRead(userId)).thenReturn(7);

        var response = controller().markAllRead(AUTH);

        assertThat(response.getBody()).isEqualTo(java.util.Map.of("updated", 7));
    }
}
