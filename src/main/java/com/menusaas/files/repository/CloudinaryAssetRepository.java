package com.menusaas.files.repository;

import com.menusaas.files.entity.CloudinaryAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CloudinaryAssetRepository extends JpaRepository<CloudinaryAsset, Long> {

    Optional<CloudinaryAsset> findBySecureUrl(String secureUrl);

    Optional<CloudinaryAsset> findByPublicId(String publicId);
}
