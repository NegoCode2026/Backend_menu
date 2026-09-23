package com.menusaas.realtime;

import com.menusaas.auth.security.UserPrincipal;
import com.menusaas.shared.api.ForbiddenException;
import com.menusaas.users.entity.Role;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * El staff solo escucha SU tenant: /topic/r/{rid}/orders exige que el rid
 * del destino sea el del JWT (SUPER_ADMIN puede todo). Sin esto, un mesero
 * podría suscribirse a los pedidos de otro restaurante.
 */
@Component
public class TenantChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TOPIC = Pattern.compile("^/topic/r/(\\d+)/orders$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Matcher matcher = destination != null ? TOPIC.matcher(destination) : null;
            if (matcher == null || !matcher.matches()) {
                throw new ForbiddenException("Destino no permitido");
            }
            long topicRid = Long.parseLong(matcher.group(1));
            UserPrincipal principal = principal(accessor);
            if (principal == null) {
                throw new ForbiddenException("No autenticado");
            }
            if (!Role.SUPER_ADMIN.equals(principal.getRole())
                    && (principal.getRestaurantId() == null || principal.getRestaurantId() != topicRid)) {
                throw new ForbiddenException("No puede escuchar pedidos de otro restaurante");
            }
        }
        return message;
    }

    private UserPrincipal principal(StompHeaderAccessor accessor) {
        Object user = accessor.getUser();
        if (user instanceof UsernamePasswordAuthenticationToken auth
                && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        return null;
    }
}
