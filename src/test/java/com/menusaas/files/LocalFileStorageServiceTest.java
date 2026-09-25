package com.menusaas.files;

import com.menusaas.config.AppProperties;
import com.menusaas.files.service.FileStorageService;
import com.menusaas.files.service.LocalFileStorageService;
import com.menusaas.shared.api.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Seguridad del almacenamiento de archivos:
 * - El MIME se valida desde los BYTES (magic bytes), no del nombre ni del header.
 * - Un nombre manipulado (path traversal) no puede escapar del directorio.
 * - Archivos que mienten su Content-Type se rechazan.
 */
class LocalFileStorageServiceTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };
    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    };
    private static final byte[] EXE_BYTES = {0x4D, 0x5A, 0x50, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
    private static final byte[] GIF89A_BYTES = {
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    private static final byte[] GIF87A_BYTES = {
            0x47, 0x49, 0x46, 0x38, 0x37, 0x61, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    private static final byte[] GIF_BAD_BYTES = {
            0x47, 0x49, 0x46, 0x38, 0x30, 0x61, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };
    private static final byte[] WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50
    };
    private static final byte[] RIFF_NOT_WEBP_BYTES = {
            0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x58, 0x58, 0x58, 0x58
    };
    private static final byte[] SHORT_BYTES = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
    private static final byte[] UNKNOWN_BYTES = {
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
    };

    @TempDir
    Path tempDir;

    private LocalFileStorageService storage;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("c2VjcmV0by1kZS1wcnVlYmEtc2VndXJvLWxvbmctZW5vdWdoLXNlY3JldA==", 15, 7),
                new AppProperties.Cors(java.util.List.of("http://localhost:4200")),
                "http://localhost:4200", "http://localhost:8080", tempDir.toString(),
                new AppProperties.Security(false, 3600, 24), new AppProperties.Payments("", "", "", ""),
                null);
        storage = new LocalFileStorageService(props);
    }

    @Test
    void store_validPng_isStoredWithServerGeneratedName() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", PNG_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        assertThat(Files.exists(tempDir.resolve(fileId))).isTrue();
    }

    @Test
    void store_clientSendsExecutable_namedPng_rejectedByMagicBytes() {
        // El nombre dice .png y el Content-Type dice image/png, pero los bytes son un EXE.
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", EXE_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_clientDeclaresDisallowedMime_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "text/html", PNG_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_pathTraversalInFilename_cannotEscapeUploadDir() {
        // El nombre original NO se usa para nada: el fileId lo genera el servidor.
        MockMultipartFile file = new MockMultipartFile("file", "../../etc/passwd.png", "image/png", PNG_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.png");
        assertThat(tempDir.resolve(fileId).normalize().startsWith(tempDir.toAbsolutePath().normalize())).isTrue();
        // El nombre original no se convirtió en ruta: no se creó nada fuera del
        // directorio de subida (portable en Windows, donde no existe /etc/passwd).
        assertThat(Files.exists(tempDir.resolve("../../etc/passwd.png"))).isFalse();
        assertThat(Files.exists(Path.of("/etc/passwd.png"))).isFalse();
    }

    @Test
    void load_fileIdWithSlashes_rejected() {
        assertThatThrownBy(() -> storage.load("../secreto"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_nonexistentFile_throws() {
        assertThatThrownBy(() -> storage.load("no-existe.png"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void load_storedFile_returnsContentAndDetectedType() {
        MockMultipartFile file = new MockMultipartFile("file", "img.png", "image/png", PNG_BYTES);
        String fileId = storage.store(file);

        FileStorageService.StoredFile stored = storage.load(fileId);

        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.content()).isEqualTo(PNG_BYTES);
    }

    @Test
    void store_jpegBytesWithExeName_getsJpgExtension() {
        // Cliente sin Content-Type declarado y nombre engañoso (.exe): los magic
        // bytes mandan → se guarda como .jpg con nombre generado por el servidor.
        MockMultipartFile file = new MockMultipartFile("file", "malware.exe", null, JPEG_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.jpg");
    }

    @Test
    void store_gif89aBytes_getsGifExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "anim", "image/gif", GIF89A_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.gif");
        assertThat(storage.load(fileId).contentType()).isEqualTo("image/gif");
    }

    @Test
    void store_gif87aBytes_isAccepted() {
        MockMultipartFile file = new MockMultipartFile("file", "viejo.gif", "image/gif", GIF87A_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).endsWith(".gif");
    }

    @Test
    void store_gifVariantBytes_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "raro.gif", "image/gif", GIF_BAD_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_webpBytes_getsWebpExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "foto", "image/webp", WEBP_BYTES);

        String fileId = storage.store(file);

        assertThat(fileId).matches("[0-9a-f-]{36}\\.webp");
        assertThat(storage.load(fileId).contentType()).isEqualTo("image/webp");
    }

    @Test
    void store_riffWithoutWebpMarker_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "audio.webp", "image/webp", RIFF_NOT_WEBP_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_shortBytes_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "corto.png", "image/png", SHORT_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void store_unknownBytes_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "raro.png", "image/png", UNKNOWN_BYTES);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void storeDirect_roundtripAndInvalidIds() {
        storage.storeDirect("directo.png", PNG_BYTES);
        assertThat(storage.load("directo.png").content()).isEqualTo(PNG_BYTES);

        // Ids inválidos se ignoran sin romper ni escribir fuera del directorio
        storage.storeDirect(null, PNG_BYTES);
        storage.storeDirect("mala/ruta!", PNG_BYTES);
        storage.storeDirect("..", PNG_BYTES);
        assertThat(Files.exists(tempDir.resolve("..").resolve("directo.png"))).isFalse();
    }

    @Test
    void load_dotDotId_rejected() {
        assertThatThrownBy(() -> storage.load(".."))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void resolvePath_validationsAndSuccess() {
        assertThatThrownBy(() -> storage.resolvePath(null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.resolvePath("mala/ruta!"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.resolvePath(".."))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.resolvePath("no-existe.png"))
                .isInstanceOf(BadRequestException.class);

        storage.storeDirect("real.png", PNG_BYTES);
        assertThat(Files.exists(storage.resolvePath("real.png"))).isTrue();
    }

    @Test
    void contentTypeForId_detectsByExtension() {
        assertThat(storage.contentTypeForId("FOTO.PNG")).isEqualTo("image/png");
        assertThat(storage.contentTypeForId("anim.gif")).isEqualTo("image/gif");
        assertThat(storage.contentTypeForId("foto.webp")).isEqualTo("image/webp");
        assertThat(storage.contentTypeForId("foto.jpg")).isEqualTo("image/jpeg");
    }
}