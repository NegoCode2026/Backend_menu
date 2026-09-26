package com.menusaas.files;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menusaas.BaseIntegrationTest;
import com.menusaas.TestHttp;
import com.menusaas.files.entity.StoredFileEntity;
import com.menusaas.files.repository.CloudinaryAssetRepository;
import com.menusaas.files.repository.StoredFileRepository;
import com.menusaas.shared.security.SignedUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Archivos extremo a extremo con Cloudinary real mockeado:
 * subir imagen → secure_url + public_id + asset persistido.
 * La lectura legacy por fileId (URLs firmadas) sigue cubierta con una fila
 * insertada directo en stored_files.
 */
class SecurityFilesIT extends BaseIntegrationTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    StoredFileRepository storedFileRepository;

    @Autowired
    CloudinaryAssetRepository assetRepository;

    @Autowired
    SignedUrlService signedUrlService;

    @MockBean
    Cloudinary cloudinary;

    private Uploader uploader;

    @org.springframework.boot.test.web.server.LocalServerPort
    int port;

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };

    private static final String SECURE_URL =
            "https://res.cloudinary.com/demo/image/upload/v1/menu_saas/r1/logo.png";
    private static final String PUBLIC_ID = "menu_saas/r1/logo";

    /** Las URLs firmadas usan la api-base-url de configuración (8080 en tests); aquí apuntan al servidor real. */
    private String localUrl(String signedUrl) {
        return signedUrl.replace("http://localhost:8080", "http://localhost:" + port);
    }

    @BeforeEach
    void setUp() {
        uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
    }

    private Map<String, Object> uploadResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("public_id", PUBLIC_ID);
        result.put("url", SECURE_URL.replace("https://", "http://"));
        result.put("secure_url", SECURE_URL);
        result.put("resource_type", "image");
        result.put("format", "png");
        result.put("bytes", PNG_BYTES.length);
        return result;
    }

    @Test
    void upload_toCloudinary_returnsSecureUrlAndPersistsAsset() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult());
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Files User", "files@test.com", "files-test");

        ResponseEntity<JsonNode> upload = uploadPng(session);

        assertThat(upload.getStatusCode().value()).isEqualTo(201);
        assertThat(upload.getBody().get("data").get("url").asText()).isEqualTo(SECURE_URL);
        assertThat(upload.getBody().get("data").get("fileId").asText()).isEqualTo(SECURE_URL);
        assertThat(upload.getBody().get("data").get("publicId").asText()).isEqualTo(PUBLIC_ID);
        assertThat(assetRepository.findByPublicId(PUBLIC_ID)).isPresent();
    }

    @Test
    void legacySignedUrl_stillServesStoredFile() {
        String fileId = UUID.randomUUID() + ".png";
        storedFileRepository.save(StoredFileEntity.builder()
                .fileId(fileId)
                .contentType("image/png")
                .data(PNG_BYTES)
                .sizeBytes((long) PNG_BYTES.length)
                .build());
        String url = signedUrlService.buildSignedUrl(fileId);

        ResponseEntity<byte[]> served = rest.getForEntity(localUrl(url), byte[].class);

        assertThat(served.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(served.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(served.getBody()).isEqualTo(PNG_BYTES);
    }

    @Test
    void signedUrl_withTamperedSignature_isRejected() {
        String fileId = UUID.randomUUID() + ".png";
        storedFileRepository.save(StoredFileEntity.builder()
                .fileId(fileId)
                .contentType("image/png")
                .data(PNG_BYTES)
                .sizeBytes((long) PNG_BYTES.length)
                .build());
        String url = signedUrlService.buildSignedUrl(fileId);
        String tampered = url.replaceFirst("sig=[0-9a-f]+", "sig=" + "0".repeat(64));

        ResponseEntity<String> response = rest.getForEntity(localUrl(tampered), String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void signedUrl_withExpiredSignature_isRejected() {
        String fileId = UUID.randomUUID() + ".png";
        storedFileRepository.save(StoredFileEntity.builder()
                .fileId(fileId)
                .contentType("image/png")
                .data(PNG_BYTES)
                .sizeBytes((long) PNG_BYTES.length)
                .build());
        String url = signedUrlService.buildSignedUrl(fileId);
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
    void upload_withDisallowedDeclaredType_isRejected() throws Exception {
        TestHttp.Session session = TestHttp.register(rest, objectMapper,
                "Files User 4", "files4@test.com", "files-test4");

        ResponseEntity<JsonNode> response = rest.exchange("/api/files/upload", HttpMethod.POST,
                new HttpEntity<>(multipart("logo.html", MediaType.TEXT_HTML), session.headers()), JsonNode.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(cloudinary);
    }

    @Test
    void upload_withoutAuth_isRejected() {
        ResponseEntity<String> response = rest.postForEntity("/api/files/upload",
                new HttpEntity<>(multipart("logo.png", MediaType.IMAGE_PNG)), String.class);

        // Sin sesión: el filtro CSRF rechaza antes que la autenticación (403).
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<JsonNode> uploadPng(TestHttp.Session session) {
        return rest.exchange("/api/files/upload", HttpMethod.POST,
                new HttpEntity<>(multipart("logo.png", MediaType.IMAGE_PNG), session.headers()), JsonNode.class);
    }

    private MultiValueMap<String, HttpEntity<?>> multipart(String filename, MediaType contentType) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", PNG_BYTES)
                .contentType(contentType)
                .filename(filename);
        return builder.build();
    }
}
