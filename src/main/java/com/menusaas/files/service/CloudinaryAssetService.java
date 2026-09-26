package com.menusaas.files.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.menusaas.files.entity.CloudinaryAsset;
import com.menusaas.files.repository.CloudinaryAssetRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Gestión real de imágenes en Cloudinary (única vía de subida).
 *
 * <p>Cada subida persiste un {@link CloudinaryAsset} con {@code public_id},
 * {@code url} y {@code secure_url}. La {@code secure_url} es el valor que se
 * guarda en {@code products.image_url} / {@code restaurants.logo_url}.
 * Las URLs externas (no son de nuestra nube o no están registradas) nunca
 * se destruyen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryAssetService {

    private final ObjectProvider<Cloudinary> cloudinaryProvider;
    private final CloudinaryAssetRepository assetRepository;

    /**
     * Sube la imagen a Cloudinary bajo {@code menu_saas/r{restaurantId}} y
     * persiste el asset. Sin cliente configurado → 503 (no hay fallback).
     */
    @Transactional
    public CloudinaryAsset upload(MultipartFile file, Long restaurantId) {
        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            throw new ServiceUnavailableException(
                    "Almacenamiento de imágenes no configurado. Configure CLOUDINARY_URL o "
                            + "CLOUDINARY_CLOUD_NAME/CLOUDINARY_API_KEY/CLOUDINARY_API_SECRET.");
        }
        String folder = "menu_saas/r" + restaurantId;
        final Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto"));
        } catch (Exception ex) {
            log.error("Fallo al subir imagen a Cloudinary: {}", ex.getMessage(), ex);
            throw new BadRequestException("No se pudo subir la imagen. Inténtelo de nuevo.");
        }

        String secureUrl = str(result.get("secure_url"));
        String publicId = str(result.get("public_id"));
        if (secureUrl.isBlank() || publicId.isBlank()) {
            throw new BadRequestException("Cloudinary no devolvió la imagen. Inténtelo de nuevo.");
        }
        String url = str(result.get("url"));
        CloudinaryAsset asset = CloudinaryAsset.builder()
                .publicId(publicId)
                .url(url.isBlank() ? secureUrl : url)
                .secureUrl(secureUrl)
                .resourceType(str(result.get("resource_type"), "image"))
                .format(str(result.get("format")))
                .bytes(num(result.get("bytes")))
                .width(numInt(result.get("width")))
                .height(numInt(result.get("height")))
                .build();
        assetRepository.save(asset);
        log.info("Imagen subida a Cloudinary: public_id={}, folder={}", publicId, folder);
        return asset;
    }

    /**
     * Destruye el asset si la URL es de nuestra nube y está registrada.
     * URLs externas, Data-URI o fileIds legacy se ignoran. Nunca lanza.
     */
    @Transactional
    public void destroyBySecureUrlIfOwned(String storedValue) {
        if (storedValue == null || storedValue.isBlank()) {
            return;
        }
        CloudinaryAsset asset = assetRepository.findBySecureUrl(storedValue.trim()).orElse(null);
        if (asset == null) {
            return;
        }
        assetRepository.delete(asset);
        Cloudinary cloudinary = cloudinaryProvider.getIfAvailable();
        if (cloudinary == null) {
            log.warn("Asset {} borrado de BD pero sin cliente Cloudinary para destroy.", asset.getPublicId());
            return;
        }
        try {
            cloudinary.uploader().destroy(asset.getPublicId(), ObjectUtils.emptyMap());
            log.info("Asset destruido en Cloudinary: {}", asset.getPublicId());
        } catch (Exception ex) {
            log.error("No se pudo destruir {} en Cloudinary: {}", asset.getPublicId(), ex.getMessage());
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String str(Object value, String fallback) {
        String s = str(value);
        return s.isBlank() ? fallback : s;
    }

    private static Long num(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Integer numInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
