package com.menusaas.files.service;

import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Set;

/**
 * Almacenamiento real y único de imágenes: Cloudinary.
 *
 * <p>La subida persiste un {@code CloudinaryAsset} (public_id, url,
 * secure_url) y devuelve la {@code secure_url}, que es el valor que se
 * guarda en {@code products.image_url} / {@code restaurants.logo_url}.
 * Sin credenciales o ante un fallo → 503/400, sin fallback.
 * La lectura ({@code load/resolvePath}) sigue delegando en BD solo para
 * fileIds legacy ya guardados.
 */
@Service
@Primary
@RequiredArgsConstructor
public class CloudinaryFileStorageService implements FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif", "image/svg+xml"
    );

    private final CloudinaryAssetService assetService;
    private final DatabaseFileStorageService databaseFileStorageService;

    @Override
    public String store(MultipartFile file) {
        if (!isSupported(file)) {
            throw new BadRequestException("Formato de imagen no soportado. Usa JPG, PNG, WEBP, GIF o SVG.");
        }
        return assetService.upload(file, SecurityUtils.currentRestaurantId()).getSecureUrl();
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
