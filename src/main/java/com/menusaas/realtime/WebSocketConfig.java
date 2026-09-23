package com.menusaas.realtime;

import com.menusaas.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP sobre /ws (SockJS para navegadores viejos).
 * - El handshake HTTP pasa por JwtAuthenticationFilter (cookies HttpOnly),
 *   así que /ws exige sesión como cualquier endpoint protegido.
 * - Tópicos: /topic/r/{restaurantId}/orders (ver TenantChannelInterceptor).
 * - Orígenes del handshake = CORS configurados (Vercel + local).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties appProperties;
    private final TenantChannelInterceptor tenantChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket NATIVO (sin SockJS): el front usa WebSocket del navegador
        // + STOMP mínimo, sin dependencias extra. El handshake HTTP pasa por
        // JwtAuthenticationFilter con las cookies HttpOnly.
        String[] origins = appProperties.cors().allowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins.length > 0 ? origins : new String[]{"*"});
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(tenantChannelInterceptor);
    }
}
