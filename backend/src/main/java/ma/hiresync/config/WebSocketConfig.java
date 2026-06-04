package ma.hiresync.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * STOMP over SockJS WebSocket configuration.
 *
 * Angular connects via: new SockJS('http://localhost:8080/ws/notifications')
 * Backend pushes to:    /user/{userId}/topic/cv-optimization
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint (brokerURL in Angular @stomp/stompjs)
        // Used by modern browsers — no SockJS dependency needed in the frontend
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*");

        // SockJS fallback endpoint (kept for older browser compatibility)
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client subscribes to /user/topic/** and /topic/**
        registry.enableSimpleBroker("/topic", "/user");
        // Client sends to /app/**
        registry.setApplicationDestinationPrefixes("/app");
        // Enables @SendToUser to route to /user/{userId}/...
        registry.setUserDestinationPrefix("/user");
    }
}
