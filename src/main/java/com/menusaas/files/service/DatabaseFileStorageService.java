package com.menusaas.files.service;

import com.menusaas.files.entity.StoredFileEntity;
import com.menusaas.files.repository.StoredFileRepository;
import com.menusaas.shared.api.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Almacenamiento de imágenes en PostgreSQL (tabla stored_files con campo BYTEA).
 * Garantiza que las imágenes persistan de forma permanente a través de reinicios
 * y redeploys de contenedores efímeros (Render, Docker).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseFileStorageService implements FileStorageService {

    /**
     * Tipos admitidos. SVG queda excluido a propósito: es un documento XML que
     * puede llevar &lt;script&gt;, y se sirve inline desde el mismo origen que la
     * app (donde el navegador adjunta la cookie de sesión). Si se necesita,
     * exigir sanitizado previo en el cliente y servirlo con Content-Disposition.
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private static final Map<String, String[]> MAGIC_BYTE_TYPES = Map.of(
            "ffd8ff", new String[]{"image/jpeg", ".jpg"},
            "89504e47", new String[]{"image/png", ".png"},
            "47494638", new String[]{"image/gif", ".gif"},
            "52494646", new String[]{"image/webp", ".webp"}
    );

    private final StoredFileRepository storedFileRepository;
    private final LocalFileStorageService localFileStorageService;

    @Override
    @Transactional
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        if (!isSupported(file)) {
            throw new BadRequestException("Formato no permitido. Use JPG, PNG, WEBP o GIF");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new BadRequestException("El archivo recibido está vacío");
            }
            String detectedType = detectContentType(bytes);
            // null = la cabecera no es de un formato admitido. Set.of() lanza NPE
            // en contains(null), así que el null se comprueba aparte.
            if (detectedType == null || !ALLOWED_TYPES.contains(detectedType)) {
                throw new BadRequestException("El contenido del archivo no es una imagen permitida");
            }

            String extension = extensionFor(detectedType);
            String fileId = UUID.randomUUID() + extension;

            StoredFileEntity entity = StoredFileEntity.builder()
                    .fileId(fileId)
                    .contentType(detectedType)
                    .data(bytes)
                    .sizeBytes(bytes.length)
                    .build();

            storedFileRepository.save(entity);
            log.info("Imagen guardada en base de datos PostgreSQL: fileId={}, size={} bytes, contentType={}",
                    fileId, bytes.length, detectedType);

            return fileId;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo cargado", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile load(String fileId) {
        if (fileId == null) {
            throw new BadRequestException("Identificador de archivo inválido");
        }

        if (fileId.startsWith("data:image/")) {
            int commaIdx = fileId.indexOf(',');
            if (commaIdx > 0) {
                String header = fileId.substring(5, commaIdx); // e.g. image/png;base64
                String contentType = header.split(";")[0];
                byte[] bytes = java.util.Base64.getDecoder().decode(fileId.substring(commaIdx + 1));
                return new StoredFile(fileId, bytes, contentType);
            }
        }

        if (!fileId.matches("[A-Za-z0-9._-]+")) {
            throw new BadRequestException("Identificador de archivo inválido");
        }

        // 1. Buscar en la base de datos PostgreSQL
        Optional<StoredFileEntity> entityOpt = storedFileRepository.findById(fileId);
        if (entityOpt.isPresent()) {
            StoredFileEntity entity = entityOpt.get();
            return new StoredFile(entity.getFileId(), entity.getData(), entity.getContentType());
        }

        // 2. Fallback a almacenamiento local (para imágenes previas en disco)
        try {
            return localFileStorageService.load(fileId);
        } catch (Exception ex) {
            log.error("Imagen no encontrada ni en BD ni en disco local: fileId={}", fileId);
            throw new BadRequestException("El archivo no existe");
        }
    }

    @Override
    public Path resolvePath(String fileId) {
        return localFileStorageService.resolvePath(fileId);
    }

    @Override
    public String contentTypeForId(String fileId) {
        return localFileStorageService.contentTypeForId(fileId);
    }

    /**
     * Filtro barato previo. NO es la validación real: la decisiva es la
     * cabecera del archivo dentro de store(). Aquí solo se descarta lo
     * evidente para no leer bytes de algo que el cliente ya declaró inválido.
     */
    @Override
    public boolean isSupported(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String declared = file.getContentType();
        return declared == null || ALLOWED_TYPES.contains(declared.toLowerCase());
    }

    /**
     * Determina el tipo por la cabecera real del archivo.
     *
     * <p>El Content-Type declarado por el cliente NUNCA se usa como resultado:
     * declararlo basta para saltarse la validación (subir un .html o un .js como
     * "image/jpeg"). Si la cabecera no corresponde a un formato de la lista
     * blanca se devuelve null y el almacenamiento lo rechaza.
     */
    String detectContentType(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        String hex = hexPrefix(bytes, 4);
        for (Map.Entry<String, String[]> entry : MAGIC_BYTE_TYPES.entrySet()) {
            if (hex.startsWith(entry.getKey())) {
                String[] candidate = entry.getValue();
                if ("image/webp".equals(candidate[0])) {
                    // RIFF____WEBP: los bytes 8-11 deben decir WEBP; si no, es otro RIFF.
                    return (bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P')
                            ? "image/webp" : null;
                }
                if ("image/gif".equals(candidate[0])) {
                    // GIF87a / GIF89a
                    return ((bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') ? "image/gif" : null;
                }
                return candidate[0];
            }
        }
        return null;
    }

    /**
     * Tipo real de la imagen o null si no es una imagen admitida.
     * Lo consumen tanto este almacenamiento como el de Cloudinary, para que
     * ninguna ruta confíe en el Content-Type declarado por el cliente.
     */
    public String detectedAllowedContentType(MultipartFile file) {
        try {
            String detected = detectContentType(file.getBytes());
            return detected != null && ALLOWED_TYPES.contains(detected) ? detected : null;
        } catch (IOException ex) {
            throw new BadRequestException("No se pudo leer el archivo recibido");
        }
    }

    private String hexPrefix(byte[] bytes, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n && i < bytes.length; i++) {
            sb.append(String.format("%02x", bytes[i]));
        }
        return sb.toString();
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
