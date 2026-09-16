package com.menusaas.admin.service;

import com.menusaas.admin.entity.AuditLog;
import com.menusaas.admin.repository.AuditLogRepository;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Auditoría best-effort: usa REQUIRES_NEW para que el registro sobreviva
 * aunque la transacción principal haga rollback, y nunca rompe la operación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String entityType, Long entityId, String detail) {
        try {
            Long actorId = null;
            String actorEmail = "system";
            try {
                actorId = SecurityUtils.currentUser().getId();
                actorEmail = SecurityUtils.currentUser().getEmail();
            } catch (Exception ignored) {
                // Sin contexto de seguridad (tareas programadas, seed)
            }
            auditLogRepository.save(AuditLog.builder()
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .detail(detail)
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo registrar auditoría {} {}: {}", action, entityType, e.getMessage());
        }
    }
}
