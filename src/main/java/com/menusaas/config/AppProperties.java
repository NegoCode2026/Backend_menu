package com.menusaas.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Cors cors,
        String appBaseUrl,
        String apiBaseUrl,
        String uploadDir,
        Security security,
        Payments payments
) {

    public AppProperties {
        if (appBaseUrl == null) appBaseUrl = "http://localhost:4200";
        if (apiBaseUrl == null) apiBaseUrl = "http://localhost:8080";
        if (uploadDir == null) uploadDir = "./uploads";
        if (cors == null) cors = new Cors(new ArrayList<>());
        if (security == null) security = new Security(false, 3600, 24, "Strict");
        if (payments == null) payments = new Payments(null, null, null, null);
    }

    public record Jwt(
            @NotBlank String secret,
            @Min(1) @Max(1440) int accessTokenTtlMinutes,
            @Min(1) @Max(168) int refreshTokenTtlHours
    ) {

        public Jwt {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("app.jwt.secret no puede estar vacío");
            }
            if (accessTokenTtlMinutes == 0) accessTokenTtlMinutes = 15;
            if (refreshTokenTtlHours == 0) refreshTokenTtlHours = 24;
        }
    }

    public record Cors(List<String> allowedOrigins) {
        public List<String> allowedOrigins() {
            return allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        }
    }

    /**
     * cookiesSecure: cookies HttpOnly/SameSite con flag Secure (obligatorio en HTTPS).
     * signedUrlTtlSeconds: tiempo de vida de las URLs firmadas de imágenes.
     * cookieSameSite: Strict (mismo-origen) | Lax | None (cross-site Vercel->túnel).
     *   None exige Secure=true (los navegadores rechazan None sin Secure).
     */
    public record Security(boolean cookiesSecure, long signedUrlTtlSeconds,
                           int sessionAbsoluteTtlHours, String cookieSameSite) {
        public Security {
            if (signedUrlTtlSeconds <= 0) signedUrlTtlSeconds = 3600;
            if (sessionAbsoluteTtlHours <= 0) sessionAbsoluteTtlHours = 24;
            if (cookieSameSite == null || cookieSameSite.isBlank()) cookieSameSite = "Strict";
            String normalized = cookieSameSite.trim();
            if (!List.of("Strict", "Lax", "None").contains(normalized)) {
                throw new IllegalArgumentException("app.security.cookie-same-site debe ser Strict|Lax|None");
            }
            cookieSameSite = normalized;
            if ("None".equals(cookieSameSite) && !cookiesSecure) {
                throw new IllegalArgumentException("SameSite=None exige COOKIES_SECURE=true");
            }
        }

        /** Compat con callers que construían Security(a, b, c). */
        public Security(boolean cookiesSecure, long signedUrlTtlSeconds, int sessionAbsoluteTtlHours) {
            this(cookiesSecure, signedUrlTtlSeconds, sessionAbsoluteTtlHours, "Strict");
        }
    }

    /**
     * ePayco: credenciales para Smart Checkout v2 (Apify API).
     * Si publicKey está vacía, la pasarela opera en modo manual (solo dev).
     */
    public record Payments(
            String epaycoPublicKey,
            String epaycoPrivateKey,
            String epaycoCustomerId,
            String epaycoPKey
    ) {
    }
}
