package com.campusops.notification.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.NotificationType;
import com.campusops.enums.Role;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.notification.dto.NotificationResponseDto;
import com.campusops.notification.entity.Notification;
import com.campusops.notification.mapper.NotificationMapper;
import com.campusops.notification.repository.NotificationRepository;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import com.campusops.user.entity.User;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Service de gestion des notifications internes. Il expose une API de creation
 * utilisee par les autres modules (reservations, imports, actions
 * administratives) ainsi que les consultations et actions de l'utilisateur
 * courant (marquer comme lu). Les creations sont volontairement tolerantes :
 * une notification destinataire manquant n'interrompt jamais l'operation
 * metier qui l'a declenchee.
 *
 * <p><b>Module 11 (§8)</b> : ce service est le point de passage unique de toute
 * creation de notification, donc le seul endroit ou les reglages s'appliquent :
 * {@code notificationsActivees} coupe l'ensemble des notifications, et
 * {@code notificationsAutomatiquesActivees} ne coupe que celles emises par le
 * <b>systeme</b> sans action utilisateur (rappels planifies, alertes systeme),
 * accessibles via {@link #notifySystemUser} et {@link #notifySystemRoles}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserRepository userRepository;
    private final AcademicYearRepository academicYearRepository;
    private final SettingsService settingsService;

    // ----- API de creation (utilisee par les autres modules) -----

    /** Cree une notification pour un utilisateur donne. */
    public void notifyUser(User recipient, NotificationType type,
                           String titre, String message, String lien) {
        if (recipient == null || !settingsService.current().isNotificationsActivees()) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .user(recipient)
                .type(type)
                .titre(titre)
                .message(message)
                .lien(lien)
                .lue(false)
                .build());
    }

    /** Cree la meme notification pour tous les utilisateurs actifs des roles donnes. */
    public void notifyRoles(Collection<Role> roles, NotificationType type,
                            String titre, String message, String lien) {
        if (roles == null || roles.isEmpty() || !settingsService.current().isNotificationsActivees()) {
            return;
        }
        for (Role role : roles) {
            for (User recipient : userRepository.findByRole(role)) {
                if (recipient.isActive()) {
                    notifyUser(recipient, type, titre, message, lien);
                }
            }
        }
    }

    /**
     * Notification emise par le systeme sans action utilisateur (rappel planifie,
     * alerte systeme) : soumise en plus au reglage « notifications automatiques ».
     */
    public void notifySystemUser(User recipient, NotificationType type,
                                 String titre, String message, String lien) {
        if (!automatiquesActivees()) {
            return;
        }
        notifyUser(recipient, type, titre, message, lien);
    }

    /** Variante par roles de {@link #notifySystemUser}. */
    public void notifySystemRoles(Collection<Role> roles, NotificationType type,
                                  String titre, String message, String lien) {
        if (!automatiquesActivees()) {
            return;
        }
        notifyRoles(roles, type, titre, message, lien);
    }

    /** Vrai si les notifications ET les notifications automatiques sont actives. */
    private boolean automatiquesActivees() {
        AppSettings reglages = settingsService.current();
        return reglages.isNotificationsActivees() && reglages.isNotificationsAutomatiquesActivees();
    }

    // ----- Consultations et actions de l'utilisateur courant -----

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyNotifications() {
        return getMyNotifications(null);
    }

    /**
     * Notifications de l'utilisateur courant, bornees a la fenetre temporelle de
     * l'annee universitaire (demandee, sinon active). Filtre temporel sans
     * migration : sans annee active ou sans bornes, la liste complete est rendue.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyNotifications(Long academicYearId) {
        YearWindow window = resolveWindow(academicYearId);
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(getCurrentUser().getId())
                .stream().filter(n -> window.covers(n.getCreatedAt()))
                .map(notificationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyUnreadNotifications() {
        return getMyUnreadNotifications(null);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponseDto> getMyUnreadNotifications(Long academicYearId) {
        YearWindow window = resolveWindow(academicYearId);
        return notificationRepository
                .findByUserIdAndLueFalseOrderByCreatedAtDesc(getCurrentUser().getId())
                .stream().filter(n -> window.covers(n.getCreatedAt()))
                .map(notificationMapper::toResponseDto).toList();
    }

    @Transactional(readOnly = true)
    public long countMyUnreadNotifications() {
        return notificationRepository.countByUserIdAndLueFalse(getCurrentUser().getId());
    }

    public NotificationResponseDto markAsRead(Long id) {
        Notification notification = findOrThrow(id);
        User currentUser = getCurrentUser();
        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new BadRequestException(
                    "Vous n'êtes pas autorisé à modifier cette notification");
        }
        if (!notification.isLue()) {
            notification.setLue(true);
            notificationRepository.save(notification);
        }
        return notificationMapper.toResponseDto(notification);
    }

    public void markAllAsRead() {
        List<Notification> unread = notificationRepository
                .findByUserIdAndLueFalseOrderByCreatedAtDesc(getCurrentUser().getId());
        if (unread.isEmpty()) {
            return;
        }
        unread.forEach(n -> n.setLue(true));
        notificationRepository.saveAll(unread);
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
        // courante, y compris hors de sa periode calendaire. On ne la borne donc
        // pas, sinon les notifications recentes disparaitraient. Seules les
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

    private Notification findOrThrow(Long id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification introuvable avec l'id " + id));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user;
        }
        if (authentication != null && authentication.getName() != null) {
            return userRepository.findByEmail(authentication.getName())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Utilisateur courant introuvable"));
        }
        throw new BadRequestException("Aucun utilisateur authentifié");
    }
}
