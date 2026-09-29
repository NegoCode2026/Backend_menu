package com.menusaas.files.controller;

import com.menusaas.files.dto.UploadFileResponse;
import com.menusaas.files.service.FileStorageService;
import com.menusaas.shared.api.ApiResponse;
import com.menusaas.shared.security.SignedUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Subida de imágenes autenticada (productos, logo, etc.). El fileId se guarda
 * en la entidad (imageUrl) a través del flujo normal de negocio y se sirve con
 * URL firmada o la URL de Cloudinary devuelta directamente.
 */
@Tag(name = "Files", description = "Subida de imágenes (requiere autenticación)")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;
    private final SignedUrlService signedUrlService;

    @Operation(summary = "Subir una imagen y obtener fileId + URL pública")
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RESTAURANT_ADMIN', 'RESTAURANT_USER', 'WAITER', 'CASHIER', 'SUPER_ADMIN')")
    public ApiResponse<UploadFileResponse> upload(@RequestParam("file") MultipartFile file) {
        String stored = fileStorageService.store(file);
        String url = signedUrlService.toSignedUrlOrNull(stored);
        return ApiResponse.ok(new UploadFileResponse(stored, url));
    }
}