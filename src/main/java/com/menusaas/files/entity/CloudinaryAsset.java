package com.menusaas.files.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Asset subido a Cloudinary (gestión real y única de imágenes).
 *
 * <p>Guarda el {@code publicId} (necesario para {@code destroy}), la
 * {@code url} http (informativa) y la {@code secureUrl} https, que es el
 * valor que se almacena en {@code products.image_url} /
 * {@code restaurants.logo_url}. Sin tenant propio (igual que
 * {@code stored_files}): la URL es pública y el aislamiento lo da no
 * exponer el public_id salvo al dueño autenticado.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cloudinary_assets")
public class CloudinaryAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 255)
    private String publicId;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "secure_url", nullable = false, unique = true, columnDefinition = "text")
    private String secureUrl;

    @Column(name = "resource_type", nullable = false, length = 20)
    @Builder.Default
    private String resourceType = "image";

    @Column(length = 20)
    private String format;

    private Long bytes;

    private Integer width;

    private Integer height;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
