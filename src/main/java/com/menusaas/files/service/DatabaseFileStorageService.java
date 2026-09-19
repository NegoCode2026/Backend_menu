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

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif", "image/svg+xml"
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
            throw new BadRequestException("Formato no permitido. Use JPG, PNG, WEBP, GIF o SVG");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length == 0) {
                throw new BadRequestException("El archivo recibido está vacío");
            }
            String detectedType = detectContentType(bytes, file.getContentType());
            if (!ALLOWED_TYPES.contains(detectedType)) {
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

            // Guardar copia local como respaldo si la carpeta existe
            try {
                localFileStorageService.storeDirect(fileId, bytes);
            } catch (Exception ex) {
                log.warn("Copia en disco local no creada para fileId={}, continuando con BD: {}", fileId, ex.getMessage());
            }

            return fileId;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo cargado", ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile load(String fileId) {
        if (fileId == null || !fileId.matches("[A-Za-z0-9._-]+")) {
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

    @Override
    public boolean isSupported(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String declared = file.getContentType();
        return declared == null || ALLOWED_TYPES.contains(declared.toLowerCase());
    }

    private String detectContentType(byte[] bytes, String declaredType) {
        if (declaredType != null && declaredType.equalsIgnoreCase("image/svg+xml")) {
            return "image/svg+xml";
        }
        if (bytes.length < 12) {
            return declaredType != null && ALLOWED_TYPES.contains(declaredType.toLowerCase())
                    ? declaredType.toLowerCase()
                    : "application/octet-stream";
        }
        String hex = hexPrefix(bytes, 4);
        for (Map.Entry<String, String[]> entry : MAGIC_BYTE_TYPES.entrySet()) {
            if (hex.startsWith(entry.getKey())) {
                String[] candidate = entry.getValue();
                if ("image/webp".equals(candidate[0])) {
                    if (bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
                        return "image/webp";
                    }
                    return "application/octet-stream";
                }
                if ("image/gif".equals(candidate[0])) {
                    if ((bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') {
                        return "image/gif";
                    }
                    return "application/octet-stream";
                }
                return candidate[0];
            }
        }
        return declaredType != null && ALLOWED_TYPES.contains(declaredType.toLowerCase())
                ? declaredType.toLowerCase()
                : "application/octet-stream";
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
            case "image/svg+xml" -> ".svg";
            default -> ".bin";
        };
    }
}
