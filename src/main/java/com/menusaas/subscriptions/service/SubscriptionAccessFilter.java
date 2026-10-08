package com.menusaas.subscriptions.service;

import com.menusaas.shared.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Corta el panel cuando la suscripción está vencida y agotada la gracia.
 *
 * <p><b>Lo que nunca se bloquea:</b>
 * <ul>
 *   <li>El menú público y los pedidos del cliente ({@code /api/public/**}). Un
 *       restaurante al que se le cae el menú pierde a sus clientes, y se lleva
 *       el problema con el negocio. Se le corta el panel, no su carta.</li>
 *   <li>Autenticación ({@code /api/auth/**}): para poder entrar y pagar.</li>
 *   <li>Suscripciones ({@code /api/subscriptions/**}): para poder pagar.</li>
 *   <li>Lecturas del propio recurso de suscripción, para que la pantalla de
 *       facturación pueda mostrar qué pasa.</li>
 *   <li>Lecturas simples: el usuario sigue viendo su panel en modo lectura.</li>
 * </ul>
 *
 * <p>Sin esto, la suscripción se registraba pero no se aplicaba: un restaurante
 * podía quedarse EXPIRED y seguir operando indefinidamente.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SubscriptionAccessFilter extends OncePerRequestFilter {

    private static final List<String> NEVER_BLOCKED_PREFIXES = List.of(
            "/api/public/",
            "/api/auth/",
            "/api/subscriptions/",
            "/api/files/",
            "/actuator"
    );

    private final SubscriptionAccessService subscriptionAccess;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!mustCheck(request)) {
            chain.doFilter(request, response);
            return;
        }

        if (!subscriptionAccess.canOperate()) {
            ErrorResponse body = ErrorResponse.of(
                    HttpStatus.PAYMENT_REQUIRED.value(),
                    "SUBSCRIPTION_REQUIRED",
                    "Tu suscripción está vencida. Actívala para seguir gestionando tu restaurante.",
                    null);
            response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        chain.doFilter(request, response);
    }

    /** Solo se comprueba lo que exige una suscripción pagada: escribir y operar. */
    private boolean mustCheck(HttpServletRequest request) {
        String path = request.getRequestURI().replaceAll("/+$", "");
        for (String prefix : NEVER_BLOCKED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        // El panel puede consultarse: solo se corta la escritura y lo que mueve
        // el negocio (pedidos, caja, mesas, usuarios).
        String method = request.getMethod().toUpperCase();
        if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method)) {
            return false;
        }
        return true;
    }
}