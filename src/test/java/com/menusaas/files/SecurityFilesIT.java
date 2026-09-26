package com.menusaas.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import com.menusaas.files.service.DatabaseFileStorageService;
import com.menusaas.shared.security.SignedUrlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Archivos extremo a extremo sin el endpoint de subida (las imágenes llegan
 * como data-URI en los payloads): archivo guardado → URL firmada → acceso
 * solo con firma válida y vigente. Sin firma no se puede acceder, aunque se
 * conozca el fileId.
 */
class SecurityFilesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    DatabaseFileStorageService storageService;

    @Autowired
    SignedUrlService signedUrlService;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };

    /** Las URLs firmadas usan la api-base-url de configuración (8080 en tests); aquí apuntan al servidor real. */
    private String localUrl(String signedUrl) {
        return signedUrl.replace("http://localhost:8080", "http://localhost:" + port);
    }

    private String storedPng() {
        return storageService.store(
                new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES));
    }

    @Test
    void uploadAndSignedAccess_fullFlow() throws Exception {
        TestHttp.register(rest, objectMapper,
                "Files User", "files@test.com", "files-test");

        String fileId = storedPng();
        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        String url = signedUrlService.buildSignedUrl(fileId);
        assertThat(url).contains("/api/public/files/" + fileId).contains("exp=").contains("sig=");

        // La URL firmada sirve la imagen con el Content-Type detectado por magic bytes.
        ResponseEntity<byte[]> served = rest.getForEntity(localUrl(url), byte[].class);
        assertThat(served.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(served.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(served.getBody()).isEqualTo(PNG_BYTES);
    }

    @Test
    void signedUrl_withTamperedSignature_isRejected() throws Exception {
        TestHttp.register(rest, objectMapper,
                "Files User 2", "files2@test.com", "files-test2");
        String url = signedUrlService.buildSignedUrl(storedPng());
        String tampered = url.replaceFirst("sig=[0-9a-f]+", "sig=" + "0".repeat(64));

        ResponseEntity<String> response = rest.getForEntity(localUrl(tampered), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void signedUrl_withExpiredSignature_isRejected() throws Exception {
        TestHttp.register(rest, objectMapper,
                "Files User 3", "files3@test.com", "files-test3");
        String url = signedUrlService.buildSignedUrl(storedPng());
        long past = Instant.now().minusSeconds(60).getEpochSecond();
        String expired = url.replaceFirst("exp=\\d+", "exp=" + past);

        ResponseEntity<String> response = rest.getForEntity(localUrl(expired), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void publicFile_withPathTraversalFileId_isRejected() {
        ResponseEntity<String> response = rest.getForEntity(
                "/api/public/files/../secret?exp=9999999999&sig=abc", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }
}