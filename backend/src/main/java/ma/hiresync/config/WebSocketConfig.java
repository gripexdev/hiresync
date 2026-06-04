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
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("http://localhost:4200", "http://localhost:4201")
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
