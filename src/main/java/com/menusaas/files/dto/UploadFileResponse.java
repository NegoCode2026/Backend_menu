package com.menusaas.files.dto;

/**
 * Resultado de una subida de imagen: fileId (almacenado en BD) y URL pública
 * (firmada si es local/BD, o HTTPS directo si Cloudinary está configurado).
 */
public record UploadFileResponse(String fileId, String url) {
}