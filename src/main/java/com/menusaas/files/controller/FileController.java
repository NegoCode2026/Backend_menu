package com.menusaas.files.controller;

import com.menusaas.files.entity.CloudinaryAsset;
import com.menusaas.files.service.CloudinaryAssetService;
import com.menusaas.files.service.CloudinaryFileStorageService;
import com.menusaas.shared.api.ApiResponse;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Files", description = "Subida de imágenes a Cloudinary (logo y productos)")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final CloudinaryAssetService assetService;
    private final CloudinaryFileStorageService storageService;

    @Operation(summary = "Subir una imagen a Cloudinary y obtener su URL segura + public_id")
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has('MENU_EDIT')")
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (!storageService.isSupported(file)) {
            throw new BadRequestException("Formato de imagen no soportado. Usa JPG, PNG, WEBP, GIF o SVG.");
        }
        CloudinaryAsset asset = assetService.upload(file, SecurityUtils.currentRestaurantId());
        return ApiResponse.ok("Imagen subida", Map.of(
                "url", asset.getSecureUrl(),
                "fileId", asset.getSecureUrl(),
                "publicId", asset.getPublicId()
        ));
    }
}
