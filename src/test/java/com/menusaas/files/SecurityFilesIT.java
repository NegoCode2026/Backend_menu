package com.menusaas.files;

import com.fasterxml.jackson.databind.JsonNode;
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

    @Test
    void upload_requiresAuth_andAcceptsImage() throws Exception {
        TestHttp.bootstrapCsrf(rest);
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Upload Owner", "upload-owner@test.com", "upload-owner");

        MockMultipartFile png = new MockMultipartFile("file", "menu.png", "image/png", PNG_BYTES);
        var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(png.getBytes()) {
            @Override
            public String getFilename() {
                return png.getOriginalFilename();
            }
        });

        org.springframework.http.HttpHeaders multipart = new org.springframework.http.HttpHeaders();
        multipart.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        multipart.add(TestHttp.HEADER_XSRF, session.xsrfToken());
        multipart.add(org.springframework.http.HttpHeaders.COOKIE,
                TestHttp.COOKIE_ACCESS + "=" + session.accessToken()
                        + "; " + TestHttp.COOKIE_REFRESH + "=" + session.refreshToken()
                        + "; " + TestHttp.COOKIE_XSRF + "=" + session.xsrfToken());
        org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> multipartEntity =
                new org.springframework.http.HttpEntity<>(body, multipart);

        ResponseEntity<JsonNode> uploaded = rest.exchange("/api/files/upload",
                org.springframework.http.HttpMethod.POST, multipartEntity, JsonNode.class);
        assertThat(uploaded.getStatusCode().value()).isEqualTo(201);
        String fileId = uploaded.getBody().get("data").get("fileId").asText();
        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        assertThat(uploaded.getBody().get("data").get("url").asText()).contains("/api/public/files/" + fileId);
    }

    @Test
    void upload_withoutAuth_isUnauthorized() throws Exception {
        MockMultipartFile png = new MockMultipartFile("file", "menu.png", "image/png", PNG_BYTES);
        var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(png.getBytes()) {
            @Override
            public String getFilename() {
                return png.getOriginalFilename();
            }
        });
        org.springframework.http.HttpHeaders multipart = new org.springframework.http.HttpHeaders();
        multipart.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        org.springframework.http.HttpEntity<org.springframework.util.MultiValueMap<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(body, multipart);

        ResponseEntity<JsonNode> response = rest.exchange("/api/files/upload",
                org.springframework.http.HttpMethod.POST, entity, JsonNode.class);
        // 401 (no autenticado) o 403 (rechazo CSRF): en ambos casos se bloquea
        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }
}