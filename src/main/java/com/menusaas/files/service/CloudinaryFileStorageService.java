package com.menusaas.files.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.menusaas.config.AppProperties;
import com.menusaas.shared.api.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de almacenamiento de imágenes en Cloudinary con fallback a almacenamiento local.
 * Si las variables de entorno de Cloudinary están configuradas, sube las imágenes a Cloudinary
 * y devuelve la URL HTTPS pública. De lo contrario, utiliza LocalFileStorageService.
 */
@Slf4j
@Service
@Primary
public class CloudinaryFileStorageService implements FileStorageService {

    /**
     * Tipos admitidos. Sin SVG por el mismo motivo que en
     * {@link DatabaseFileStorageService}: puede llevar script y se sirve inline
     * desde el origen de la aplicación.
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif"
    );

    private final Cloudinary cloudinary;
    private final DatabaseFileStorageService databaseFileStorageService;
    private final boolean configured;

    public CloudinaryFileStorageService(AppProperties appProperties, DatabaseFileStorageService databaseFileStorageService) {
        this.databaseFileStorageService = databaseFileStorageService;
        AppProperties.Cloudinary config = appProperties.cloudinary();

        Cloudinary instance = null;
        boolean isConf = false;

        if (config != null && config.isConfigured()) {
            try {
                if (config.url() != null && !config.url().isBlank()) {
                    instance = new Cloudinary(config.url());
                } else {
                    instance = new Cloudinary(ObjectUtils.asMap(
                            "cloud_name", config.cloudName(),
                            "api_key", config.apiKey(),
                            "api_secret", config.apiSecret(),
                            "secure", true
                    ));
                }
                isConf = true;
                log.info("CloudinaryFileStorageService inicializado correctamente con Cloudinary.");
            } catch (Exception ex) {
                log.error("Error al inicializar cliente Cloudinary, usando almacenamiento en BD: {}", ex.getMessage());
            }
        } else {
            // Revisa si existe la variable de entorno estándar CLOUDINARY_URL
            String envUrl = System.getenv("CLOUDINARY_URL");
            if (envUrl != null && !envUrl.isBlank()) {
                try {
                    instance = new Cloudinary(envUrl);
                    isConf = true;
                    log.info("CloudinaryFileStorageService inicializado desde variable de entorno CLOUDINARY_URL.");
                } catch (Exception ex) {
                    log.error("Error al inicializar Cloudinary desde CLOUDINARY_URL: {}", ex.getMessage());
                }
            }
        }

        this.cloudinary = instance;
        this.configured = isConf;
        if (!configured) {
            log.info("Cloudinary no está configurado. Las imágenes se guardarán en la base de datos PostgreSQL.");
        }
    }

    @Override
    public String store(MultipartFile file) {
        if (!isSupported(file)) {
            throw new BadRequestException("Formato de imagen no soportado. Usa JPG, PNG, WEBP o GIF.");
        }
        // El tipo declarado por el cliente no decide nada: se comprueba la cabecera
        // real del archivo. Sin esto, "resource_type: auto" subiría a Cloudinary
        // cualquier contenido (un HTML, un JS) solo con declarar image/jpeg.
        String detected = databaseFileStorageService.detectedAllowedContentType(file);
        if (detected == null) {
            throw new BadRequestException("El contenido del archivo no es una imagen permitida");
        }

        if (!configured || cloudinary == null) {
            return databaseFileStorageService.store(file);
        }

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "menu_saas_products",
                    "resource_type", "auto"
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            if (secureUrl != null && !secureUrl.isBlank()) {
                log.info("Imagen subida exitosamente a Cloudinary: {}", secureUrl);
                return secureUrl;
            }

            log.warn("Cloudinary no retornó 'secure_url'. Usando fallback de base de datos.");
            return databaseFileStorageService.store(file);
        } catch (IOException ex) {
            log.error("Fallo al subir imagen a Cloudinary, reintentando con almacenamiento en BD: {}", ex.getMessage(), ex);
            return databaseFileStorageService.store(file);
        }
    }

    @Override
    public Path resolvePath(String fileId) {
        return databaseFileStorageService.resolvePath(fileId);
    }

    @Override
    public String contentTypeForId(String fileId) {
        return databaseFileStorageService.contentTypeForId(fileId);
    }

    @Override
    public boolean isSupported(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;
        String contentType = file.getContentType();
        return contentType != null && ALLOWED_TYPES.contains(contentType.toLowerCase());
    }

    @Override
    public StoredFile load(String fileId) {
        return databaseFileStorageService.load(fileId);
    }
}
