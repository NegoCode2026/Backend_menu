package com.menusaas.shared.http;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Fábrica de clientes HTTP salientes con timeouts obligatorios.
 *
 * <p>Spring, por defecto, usa read timeout INFINITO. Una llamada a un proveedor
 * externo (WhatsApp, ePayco) que se queda colgada retiene conexión del pool y la
 * transacción que la envuelve de forma indefinida: con pocos casos simultáneos
 * el pool se agota y deja de responder toda la aplicación, no solo esa función.
 *
 * <p>Nadie debe construir un cliente HTTP con el constructor por defecto: usar
 * {@link #restTemplate} o {@link #restClient}.
 */
public final class HttpClientFactory {

    private HttpClientFactory() {
    }

    public static RestTemplate restTemplate(Duration connectTimeout, Duration readTimeout) {
        return new RestTemplate(requestFactory(connectTimeout, readTimeout));
    }

    public static RestClient restClient(Duration connectTimeout, Duration readTimeout) {
        return RestClient.builder()
                .requestFactory(requestFactory(connectTimeout, readTimeout))
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }
}