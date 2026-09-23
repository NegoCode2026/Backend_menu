package com.menusaas.files.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * Abstracción de almacenamiento de imágenes.
 * La implementación local guarda en disco; el acceso público se hace mediante
 * URLs firmadas con expiración (nunca sirviendo el directorio directamente).
 */
public interface FileStorageService {

    /**
     * Almacena la imagen y devuelve el fileId (nombre seguro generado por el servidor).
     */
    String store(MultipartFile file);

    /**
     * Devuelve el Path absoluto del archivo para streaming eficiente.
     * Lanza BadRequestException si el fileId es inválido o el archivo no existe.
     */
    Path resolvePath(String fileId);

    /**
     * Infiere el Content-Type a partir de la extensión del fileId.
     */
    String contentTypeForId(String fileId);

    boolean isSupported(MultipartFile file);

    /**
     * Carga un archivo ya almacenado (fileId). Lanza BadRequestException si no existe.
     * @deprecated Prefiere resolvePath() + PathResource para no cargar bytes en heap.
     */
    @Deprecated
    StoredFile load(String fileId);

    /**
     * Archivo almacenado junto con su Content-Type detectado.
     */
    record StoredFile(String fileId, byte[] content, String contentType) {
    }
}