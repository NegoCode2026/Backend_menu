package com.menusaas.admin.repository;

import com.menusaas.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    default Page<AuditLog> search(String actorEmail, String action, String entityType, Long entityId, Pageable pageable) {
        Specification<AuditLog> spec = Specification.allOf(
                textLike("actorEmail", actorEmail),
                textLike("action", action),
                textLike("entityType", entityType),
                equals("entityId", entityId)
        );
        return findAll(spec, pageable);
    }

    private static Specification<AuditLog> textLike(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.like(cb.lower(root.get(field)), "%" + value.toLowerCase() + "%");
    }

    private static Specification<AuditLog> equals(String field, Object value) {
        if (value == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }
}