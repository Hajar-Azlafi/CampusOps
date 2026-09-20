package com.campusops.audit.repository;

import com.campusops.audit.entity.AuditLog;
import com.campusops.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Detache l'utilisateur des entrees d'audit sans les supprimer. La colonne
     * {@code user_id} est nullable : a la suppression d'un compte, on anonymise
     * son journal d'audit (l'historique systeme est conserve, §5/§19).
     */
    @Modifying
    @Query("UPDATE AuditLog a SET a.user = null WHERE a.user.id = :userId")
    int detachUser(@Param("userId") Long userId);

    /**
     * Recherche multi-criteres dans le journal d'audit. Chaque critere est
     * facultatif : s'il est nul, il n'est pas applique. Les resultats sont
     * ordonnes du plus recent au plus ancien.
     */
    @Query("SELECT a FROM AuditLog a WHERE "
            + "(:userId IS NULL OR a.user.id = :userId) AND "
            + "(:action IS NULL OR a.action = :action) AND "
            + "(:module IS NULL OR LOWER(a.module) = LOWER(:module)) AND "
            + "(:start IS NULL OR a.createdAt >= :start) AND "
            + "(:end IS NULL OR a.createdAt <= :end) "
            + "ORDER BY a.createdAt DESC")
    List<AuditLog> search(@Param("userId") Long userId,
                          @Param("action") AuditAction action,
                          @Param("module") String module,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end);
}
