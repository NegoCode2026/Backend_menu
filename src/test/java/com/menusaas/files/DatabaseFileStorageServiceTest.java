package com.menusaas.files;

import com.menusaas.config.AppProperties;
import com.menusaas.files.entity.StoredFileEntity;
import com.menusaas.files.repository.StoredFileRepository;
import com.menusaas.files.service.DatabaseFileStorageService;
import com.menusaas.files.service.FileStorageService;
import com.menusaas.files.service.LocalFileStorageService;
import com.menusaas.shared.api.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Almacenamiento en BD: validación por magic bytes (con soporte SVG por
 * declaración), lectura (data-URI, BD y fallback a disco) y validaciones.
 */
@ExtendWith(MockitoExtension.class)
class DatabaseFileStorageServiceTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };
    private static final byte[] GIF89A_BYTES = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    private static final byte[] WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };
    private static final byte[] RIFF_NOT_WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x58, 0x58, 0x58, 0x58
    };
    private static final byte[] SHORT_BYTES = {0x01, 0x02, 0x03, 0x04};
    private static final byte[] SVG_BYTES = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Mock
    private StoredFileRepository repository;

    private DatabaseFileStorageService storage;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", tempDir.toString(),
                new AppProperties.Security(false, 3600, 24), new AppProperties.Payments("", "", "", ""),
                null);
        storage = new DatabaseFileStorageService(repository, new LocalFileStorageService(props));
        lenient().when(repository.save(any(StoredFileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void store_nullOrEmpty_rejected() {
        assertThatThrownBy(() -> storage.store(null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "vacio.png", "image/png", new byte[0])))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_disallowedDeclaredType_rejected() {
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "x.html", "text/html", PNG_BYTES)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_png_ok() {
        String fileId = storage.store(
                new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES));

        assertThat(fileId).endsWith(".png");
    }

    @Test
    void store_jpegUppercaseDeclared_ok() {
        String fileId = storage.store(
                new MockMultipartFile("file", "foto.jpg", "IMAGE/JPEG", JPEG_BYTES));

        assertThat(fileId).endsWith(".jpg");
    }

    @Test
    void store_svgByDeclaration_ok() {
        String fileId = storage.store(
                new MockMultipartFile("file", "logo.svg", "image/svg+xml", SVG_BYTES));

        assertThat(fileId).endsWith(".svg");
    }

    @Test
    void store_gifAndWebp_ok() {
        assertThat(storage.store(
                new MockMultipartFile("file", "a.gif", "image/gif", GIF89A_BYTES))).endsWith(".gif");
        assertThat(storage.store(
                new MockMultipartFile("file", "a.webp", "image/webp", WEBP_BYTES))).endsWith(".webp");
    }

    @Test
    void store_riffWithoutWebp_rejected() {
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "a.webp", "image/webp", RIFF_NOT_WEBP_BYTES)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_shortBytesWithAllowedDeclaration_usesDeclaredType() {
        String fileId = storage.store(
                new MockMultipartFile("file", "mini.png", "image/png", SHORT_BYTES));

        assertThat(fileId).endsWith(".png");
    }

    @Test
    void store_shortBytesWithoutDeclaration_rejected() {
        assertThatThrownBy(() -> storage.store(
                new MockMultipartFile("file", "mini", null, SHORT_BYTES)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_unknownBytesWithAllowedDeclaration_usesDeclaredType() {
        byte[] unknown = new byte[12];
        String fileId = storage.store(
                new MockMultipartFile("file", "x.png", "image/png", unknown));

        assertThat(fileId).endsWith(".png");
    }

    @Test
    void load_null_rejected() {
        assertThatThrownBy(() -> storage.load(null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_dataUri_returnsDecodedContent() {
        String payload = Base64.getEncoder().encodeToString(PNG_BYTES);
        FileStorageService.StoredFile stored = storage.load("data:image/png;base64," + payload);

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.content()).isEqualTo(PNG_BYTES);
    }

    @Test
    void load_dataUriWithoutComma_rejected() {
        assertThatThrownBy(() -> storage.load("data:image/png;base64"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_invalidId_rejected() {
        assertThatThrownBy(() -> storage.load("mala/ruta!"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_fromDatabase_returnsEntity() {
        StoredFileEntity entity = StoredFileEntity.builder()
                .fileId("abc123.png").contentType("image/png").data(PNG_BYTES).sizeBytes(PNG_BYTES.length)
                .build();
        when(repository.findById("abc123.png")).thenReturn(Optional.of(entity));

        FileStorageService.StoredFile stored = storage.load("abc123.png");

        assertThat(stored.content()).isEqualTo(PNG_BYTES);
        assertThat(stored.contentType()).isEqualTo("image/png");
    }

    @Test
    void load_missingInDb_fallsBackToLocal() {
        when(repository.findById("local.png")).thenReturn(Optional.empty());
        new LocalFileStorageService(localProps()).storeDirect("local.png", PNG_BYTES);

        FileStorageService.StoredFile stored = storage.load("local.png");

        assertThat(stored.content()).isEqualTo(PNG_BYTES);
    }

    @Test
    void load_missingEverywhere_rejected() {
        when(repository.findById("nada.png")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storage.load("nada.png"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void isSupported_validations() {
        assertThat(storage.isSupported(null)).isFalse();
        assertThat(storage.isSupported(
                new MockMultipartFile("file", "v", "image/png", new byte[0]))).isFalse();
        assertThat(storage.isSupported(
                new MockMultipartFile("file", "f.PNG", "IMAGE/PNG", PNG_BYTES))).isTrue();
        assertThat(storage.isSupported(
                new MockMultipartFile("file", "f.png", null, PNG_BYTES))).isTrue();
        assertThat(storage.isSupported(
                new MockMultipartFile("file", "f.html", "text/html", PNG_BYTES))).isFalse();
    }

    private AppProperties localProps() {
        return new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", tempDir.toString(),
                new AppProperties.Security(false, 3600, 24), new AppProperties.Payments("", "", "", ""),
                null);
    }
}
