package com.menusaas.shared.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Lock de liderazgo para tareas {@code @Scheduled}, respaldado por el advisory
 * lock de PostgreSQL.
 *
 * <p>Con una sola instancia cada job corre una vez, pero al añadir réplicas
 * TODAS ejecutarían el mismo {@code @Scheduled}: dosjobs expirando
 * suscripciones a la vez compiten por las mismas filas y una puede fallar con
 * violación de clave foránea o escribir un estado ya cambiado.
 *
 * <p>Se usa el lock de TRANSACCIÓN ({@code pg_try_advisory_xact_lock}) y no el
 * de sesión a propósito: se libera solo al hacer commit o rollback, así que no
 * puede quedar huérfano si el proceso muere a mitad del job. El servicio que
 * lo usa debe llamar a {@link #tryRun} dentro de su transacción.
 *
 * <p>Cada tarea usa su propia clave: dos jobs distintos no se bloquean entre sí.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledJobLock {

    /**
     * Claves fijas y distintas por tarea. Elegir un hash por nombre sería frágil
     * (un refactorRename cambiaría la clave y solaparía dos jobs).
     */
    public static final long EXPIRE_SUBSCRIPTIONS = 1001L;
    public static final long PURGE_REFRESH_TOKENS = 1002L;

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return true si esta instancia consiguió el lock y debe ejecutar el job.
     */
    public boolean tryAcquire(long key) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "select pg_try_advisory_xact_lock(?)", Boolean.class, key);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Ejecuta el job solo si esta instancia consigue el lock. Silencioso si
     * otra lo tiene: es el comportamiento normal en réplicas, no un error.
     */
    public void tryRun(long key, String jobName, Runnable job) {
        if (!tryAcquire(key)) {
            log.debug("Job {} omitido: otra instancia lo está ejecutando.", jobName);
            return;
        }
        job.run();
    }
}