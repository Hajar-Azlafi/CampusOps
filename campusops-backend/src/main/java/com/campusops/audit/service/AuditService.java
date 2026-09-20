package com.campusops.audit.service;

import com.campusops.audit.dto.AuditLogResponseDto;
import com.campusops.audit.entity.AuditLog;
import com.campusops.audit.mapper.AuditLogMapper;
import com.campusops.audit.repository.AuditLogRepository;
import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.AuditAction;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service du journal d'audit. Il expose une API d'enregistrement utilisee par
 * les autres modules ainsi que les consultations (journal complet, recherche
 * multi-criteres, historique personnel). L'enregistrement est tolerant aux
 * erreurs : il ne doit jamais interrompre l'operation metier tracee.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuditService {

    private static final int MAX_IP_LENGTH = 60;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;
    private final UserRepository userRepository;
    private final AcademicYearRepository academicYearRepository;
    private final HttpServletRequest request;

    // ----- API d'enregistrement (utilisee par les autres modules) -----

    /** Enregistre une action pour un utilisateur explicite. */
    public void record(User user, AuditAction action, String module, String description) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .user(user)
                    .action(action)
                    .module(module)
                    .description(description)
                    .adresseIp(resolveClientIp())
                    .build());
        } catch (Exception ex) {
            // L'audit ne doit jamais faire echouer l'action metier tracee.
            log.warn("Echec d'enregistrement de l'audit ({} / {}) : {}",
                    action, module, ex.getMessage());
        }
    }

    /** Enregistre une action pour l'utilisateur courant (resolu depuis le contexte). */
    public void record(AuditAction action, String module, String description) {
        record(currentUserOrNull(), action, module, description);
    }

    // ----- Consultations -----

    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getAllAudits() {
        return getAllAudits(null);
    }

    /**
     * Journal complet, borne a la fenetre temporelle de l'annee universitaire
     * (demandee, sinon active). Filtre temporel sans migration : un id nul sans
     * annee active, ou une annee sans bornes, renvoie le journal complet.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getAllAudits(Long academicYearId) {
        YearWindow window = resolveWindow(academicYearId);
        return auditLogRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> window.covers(a.getCreatedAt()))
                .map(auditLogMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> search(Long userId, AuditAction action, String module,
                                            LocalDateTime start, LocalDateTime end) {
        String normalizedModule = (module != null && !module.isBlank()) ? module.trim() : null;
        return auditLogRepository.search(userId, action, normalizedModule, start, end)
                .stream().map(auditLogMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getMyHistory() {
        return getMyHistory(null);
    }

    /** Historique personnel, borne a la fenetre temporelle de l'annee universitaire. */
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getMyHistory(Long academicYearId) {
        YearWindow window = resolveWindow(academicYearId);
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(getCurrentUser().getId())
                .stream()
                .filter(a -> window.covers(a.getCreatedAt()))
                .map(auditLogMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getHistory(Long userId) {
        return getHistory(userId, null);
    }

    /** Historique d'un utilisateur (ou de tous), borne a l'annee universitaire. */
    @Transactional(readOnly = true)
    public List<AuditLogResponseDto> getHistory(Long userId, Long academicYearId) {
        if (userId != null) {
            if (!userRepository.existsById(userId)) {
                throw new ResourceNotFoundException("Utilisateur introuvable avec l'id " + userId);
            }
            YearWindow window = resolveWindow(academicYearId);
            return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(a -> window.covers(a.getCreatedAt()))
                    .map(auditLogMapper::toResponseDto).toList();
        }
        return getAllAudits(academicYearId);
    }

    // ----- Helpers -----

    /**
     * Fenetre temporelle d'une annee universitaire, appliquee a {@code createdAt}.
     * Des bornes nulles neutralisent le filtre correspondant (repli non cassant).
     */
    private record YearWindow(LocalDateTime debut, LocalDateTime fin) {
        boolean covers(LocalDateTime instant) {
            if (instant == null) {
                return debut == null && fin == null;
            }
            if (debut != null && instant.isBefore(debut)) {
                return false;
            }
            return fin == null || !instant.isAfter(fin);
        }
    }

    /** Resout la fenetre de l'annee demandee, sinon active, sinon « toutes annees ». */
    private YearWindow resolveWindow(Long academicYearId) {
        AcademicYear year = (academicYearId != null)
                ? academicYearRepository.findById(academicYearId).orElse(null)
                : academicYearRepository.findFirstByActifTrue().orElse(null);
        if (year == null) {
            return new YearWindow(null, null);
        }
        // L'annee ACTIVE est le contexte « vivant » : elle possede l'activite
        // courante (connexions, actions du jour), y compris avant le debut
        // calendaire du semestre ou apres sa fin. On ne borne donc pas l'annee
        // active — sinon les evenements temps reel (LOGIN/LOGOUT...) survenant
        // hors de sa periode calendaire disparaitraient du journal. Seules les
        // annees HISTORIQUES sont bornees par leur periode.
        if (year.isActif()) {
            return new YearWindow(null, null);
        }
        LocalDate debut = year.getDateDebut();
        LocalDate fin = year.getDateFin();
        return new YearWindow(
                debut != null ? debut.atStartOfDay() : null,
                fin != null ? fin.atTime(23, 59, 59) : null);
    }

    /** Extrait l'adresse IP du client, en tenant compte d'un eventuel proxy. */
    private String resolveClientIp() {
        try {
            String forwarded = request.getHeader("X-Forwarded-For");
            String ip = (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr();
            if (ip == null) {
                return null;
            }
            return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
        } catch (Exception ex) {
            // Contexte hors requete HTTP : l'adresse IP est simplement absente.
            return null;
        }
    }

    private User currentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        if (authentication != null && authentication.getName() != null) {
            return userRepository.findByEmail(authentication.getName()).orElse(null);
        }
        return null;
    }

    private User getCurrentUser() {
        User user = currentUserOrNull();
        if (user == null) {
            throw new BadRequestException("Aucun utilisateur authentifié");
        }
        return user;
    }
}
