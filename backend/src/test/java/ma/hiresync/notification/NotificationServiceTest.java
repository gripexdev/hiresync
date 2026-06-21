package ma.hiresync.notification;

import ma.hiresync.notification.entity.Notification;
import ma.hiresync.notification.entity.NotificationType;
import ma.hiresync.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private SimpMessagingTemplate messaging;
    @Mock private NotificationRepository repo;

    private NotificationService service() {
        return new NotificationService(messaging, repo);
    }

    @Test
    void create_savesNotificationWithGivenFields() {
        UUID userId = UUID.randomUUID();
        when(repo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        var saved = service().create(userId, NotificationType.CV_OPTIMIZED,
                "CV optimisé", "Score ATS amélioré", "/cv/history");

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getType()).isEqualTo(NotificationType.CV_OPTIMIZED);
        assertThat(saved.getTitle()).isEqualTo("CV optimisé");
        assertThat(saved.getLink()).isEqualTo("/cv/history");
    }

    @Test
    void unreadCount_delegatesToRepositoryCount() {
        UUID userId = UUID.randomUUID();
        when(repo.countByUserIdAndReadFalse(userId)).thenReturn(3L);

        assertThat(service().unreadCount(userId)).isEqualTo(3L);
    }

    @Test
    void markRead_existingUnreadNotification_marksReadAndSaves() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        Notification notif = Notification.builder().id(notifId).userId(userId).read(false).build();
        when(repo.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(notif));

        service().markRead(notifId, userId);

        assertThat(notif.isRead()).isTrue();
        verify(repo).save(notif);
    }

    @Test
    void markRead_alreadyRead_doesNotSaveAgain() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        Notification notif = Notification.builder().id(notifId).userId(userId).read(true).build();
        when(repo.findByIdAndUserId(notifId, userId)).thenReturn(Optional.of(notif));

        service().markRead(notifId, userId);

        verify(repo, never()).save(any());
    }

    @Test
    void markRead_notificationBelongingToSomeoneElse_isNoOp() {
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        when(repo.findByIdAndUserId(notifId, userId)).thenReturn(Optional.empty());

        service().markRead(notifId, userId);

        verify(repo, never()).save(any());
    }

    @Test
    void markAllRead_returnsRepositoryUpdatedCount() {
        UUID userId = UUID.randomUUID();
        when(repo.markAllReadForUser(userId)).thenReturn(5);

        assertThat(service().markAllRead(userId)).isEqualTo(5);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pushCvOptimizationEvent_sendsToUserSpecificTopicWithProvider() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();

        service().pushCvOptimizationEvent(userId, optimId, "completed", "Score 82%", "Groq Llama 3.3 70B");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq(userId.toString()), eq("/topic/cv-optimization"), payloadCaptor.capture());

        var payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("optimizationId")).isEqualTo(optimId.toString());
        assertThat(payload.get("status")).isEqualTo("completed");
        assertThat(payload.get("provider")).isEqualTo("Groq Llama 3.3 70B");
    }

    @Test
    @SuppressWarnings("unchecked")
    void pushCvOptimizationEvent_withoutProvider_omitsProviderKey() {
        UUID userId = UUID.randomUUID();
        UUID optimId = UUID.randomUUID();

        service().pushCvOptimizationEvent(userId, optimId, "failed", "Erreur");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq(userId.toString()), eq("/topic/cv-optimization"), payloadCaptor.capture());

        var payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).doesNotContainKey("provider");
    }
}
