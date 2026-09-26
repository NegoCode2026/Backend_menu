package com.menusaas.files;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.menusaas.files.entity.CloudinaryAsset;
import com.menusaas.files.repository.CloudinaryAssetRepository;
import com.menusaas.files.service.CloudinaryAssetService;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Cloudinary real sin red: el cliente se mockea. Cubre persistencia del
 * asset (public_id/url/secure_url), 503 sin cliente, fallos de subida y
 * destroy solo de URLs registradas.
 */
@ExtendWith(MockitoExtension.class)
class CloudinaryAssetServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private CloudinaryAssetRepository assetRepository;

    @Mock
    private ObjectProvider<Cloudinary> cloudinaryProvider;

    private CloudinaryAssetService assetService;

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52
    };

    @BeforeEach
    void setUp() {
        lenient().when(cloudinaryProvider.getIfAvailable()).thenReturn(cloudinary);
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        assetService = new CloudinaryAssetService(cloudinaryProvider, assetRepository);
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "logo.png", "image/png", PNG);
    }

    private Map<String, Object> uploadResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("public_id", "menu_saas/r1/logo");
        result.put("url", "http://res.cloudinary.com/demo/image/upload/v1/menu_saas/r1/logo.png");
        result.put("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/menu_saas/r1/logo.png");
        result.put("resource_type", "image");
        result.put("format", "png");
        result.put("bytes", 16);
        result.put("width", 64);
        result.put("height", 64);
        return result;
    }

    @Test
    void upload_persistsAssetWithPublicIdAndSecureUrl() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(uploadResult());

        CloudinaryAsset asset = assetService.upload(png(), 1L);

        assertThat(asset.getPublicId()).isEqualTo("menu_saas/r1/logo");
        assertThat(asset.getSecureUrl()).startsWith("https://");
        assertThat(asset.getFormat()).isEqualTo("png");
        assertThat(asset.getBytes()).isEqualTo(16L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        verify(uploader).upload(any(byte[].class), options.capture());
        assertThat(options.getValue()).containsEntry("folder", "menu_saas/r1");

        ArgumentCaptor<CloudinaryAsset> saved = ArgumentCaptor.forClass(CloudinaryAsset.class);
        verify(assetRepository).save(saved.capture());
        assertThat(saved.getValue().getSecureUrl()).isEqualTo(asset.getSecureUrl());
    }

    @Test
    void upload_withoutClient_throws503() {
        when(cloudinaryProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> assetService.upload(png(), 1L))
                .isInstanceOf(ServiceUnavailableException.class);
        verifyNoInteractions(uploader, assetRepository);
    }

    @Test
    void upload_whenCloudinaryFails_throws400() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap()))
                .thenThrow(new java.io.IOException("red caída"));

        assertThatThrownBy(() -> assetService.upload(png(), 1L))
                .isInstanceOf(BadRequestException.class);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void upload_withoutSecureUrl_throws400() throws Exception {
        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of());

        assertThatThrownBy(() -> assetService.upload(png(), 1L))
                .isInstanceOf(BadRequestException.class);
        verify(assetRepository, never()).save(any());
    }

    @Test
    void destroy_registeredUrl_deletesRowAndCallsDestroy() throws Exception {
        CloudinaryAsset asset = CloudinaryAsset.builder()
                .publicId("menu_saas/r1/logo")
                .url("http://x/logo.png")
                .secureUrl("https://x/logo.png")
                .build();
        when(assetRepository.findBySecureUrl("https://x/logo.png")).thenReturn(Optional.of(asset));
        when(uploader.destroy(eq("menu_saas/r1/logo"), anyMap()))
                .thenReturn(Map.of("result", "ok"));

        assetService.destroyBySecureUrlIfOwned("https://x/logo.png");

        verify(assetRepository).delete(asset);
        verify(uploader).destroy(eq("menu_saas/r1/logo"), anyMap());
    }

    @Test
    void destroy_unregisteredUrl_doesNothing() {
        when(assetRepository.findBySecureUrl("https://unsplash.com/foto.png")).thenReturn(Optional.empty());

        assetService.destroyBySecureUrlIfOwned("https://unsplash.com/foto.png");

        verify(assetRepository, never()).delete(any());
        verifyNoInteractions(uploader);
    }

    @Test
    void destroy_blankOrNull_doesNothing() {
        assetService.destroyBySecureUrlIfOwned(null);
        assetService.destroyBySecureUrlIfOwned("  ");

        verifyNoInteractions(assetRepository, uploader);
    }

    @Test
    void destroy_whenCloudinaryFails_deletesRowAnyway() throws Exception {
        CloudinaryAsset asset = CloudinaryAsset.builder()
                .publicId("menu_saas/r1/logo")
                .url("http://x/logo.png")
                .secureUrl("https://x/logo.png")
                .build();
        when(assetRepository.findBySecureUrl("https://x/logo.png")).thenReturn(Optional.of(asset));
        when(uploader.destroy(eq("menu_saas/r1/logo"), anyMap()))
                .thenThrow(new RuntimeException("timeout"));

        assetService.destroyBySecureUrlIfOwned("https://x/logo.png");

        verify(assetRepository).delete(asset);
    }
}
