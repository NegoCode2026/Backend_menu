package com.menusaas.files.repository;

import com.menusaas.files.entity.StoredFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, String> {
}
