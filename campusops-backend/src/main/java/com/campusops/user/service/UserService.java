package com.campusops.user.service;

import com.campusops.audit.service.AuditService;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionExecutor;
import com.campusops.deletion.DeletionImpact;
import com.campusops.department.repository.DepartmentRepository;
import com.campusops.enums.AuditAction;
import com.campusops.enums.NotificationType;
import com.campusops.enums.Role;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.mail.EmailService;
import com.campusops.notification.service.NotificationService;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.security.AccessScopeService;
import com.campusops.user.dto.PasswordResetResponseDto;
import com.campusops.user.dto.UserRequestDto;
import com.campusops.user.dto.UserResponseDto;
import com.campusops.user.entity.User;
import com.campusops.user.mapper.UserMapper;
import com.campusops.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Comparator;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class UserService {

    /** Module trace dans le journal d'audit. */
    private static final String MODULE = "Utilisateurs";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final AccessScopeService accessScope;
    private final DepartmentRepository departmentRepository;
    private final ProgramRepository programRepository;
    private final DeletionAnalyzer deletionAnalyzer;
    private final DeletionExecutor deletionExecutor;

    public UserResponseDto createUser(UserRequestDto request) {
        accessScope.requireAdmin();
        validateDepartment(request.getRole(), request.getDepartment());
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Un utilisateur avec l'email " + request.getEmail() + " existe déjà");
        }

        User user = userMapper.toEntity(request);
        String temporaryPassword = passwordService.generateTemporaryPassword();
        user.setPassword(passwordService.encode(temporaryPassword));
        user.setActive(true);
        user.setMustChangePassword(true);

        User savedUser = userRepository.save(user);
        auditService.record(AuditAction.CREATE, MODULE,
                "Creation de l'utilisateur " + fullName(savedUser) + " (" + savedUser.getEmail() + ")");

        // Envoi de l'e-mail d'identifiants. Le mot de passe temporaire en clair ne
        // vit que le temps de cet appel : il part par e-mail et n'est ni stocke ni
        // renvoye par l'API. Un echec d'envoi n'annule PAS la creation du compte.
        log.info("[EMAIL] Demande d'envoi d'identifiants pour {}", savedUser.getEmail());
        boolean emailSent = emailService.sendUserCreatedEmail(savedUser, temporaryPassword);
        if (!emailSent) {
            alertCredentialsEmailFailure(savedUser);
        }

        if (emailSent) {
            // Notifier les administrateurs sans jamais persister ni diffuser le secret
            try {
                notificationService.notifyRoles(EnumSet.of(Role.ADMIN), NotificationType.INFO,
                        "Envoi des identifiants",
                        "Les identifiants de " + fullName(savedUser) + " (" + savedUser.getEmail() + ") ont été envoyés.",
                        null);
            } catch (Exception ex) {
                // Ne jamais laisser planter l'opération principale pour une notification
                log.warn("Impossible d'envoyer la notification d'envoi d'identifiants aux administrateurs", ex);
            }
        }

        UserResponseDto response = userMapper.toResponseDto(savedUser);
        response.setEmailSent(emailSent);
        response.setTemporaryPassword(temporaryPassword);
        return response;
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = findUserOrThrow(id);
        return userMapper.toResponseDto(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return sortUsers(userRepository.findAll()).stream()
                .map(userMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> searchUsers(String keyword) {
        return sortUsers(userRepository.searchByKeyword(keyword)).stream()
                .map(userMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> filterUsers(Role role, String department, Boolean actif) {
        List<User> users;
        if (role != null && department != null) {
            users = userRepository.findByRoleAndDepartment(role, department);
        } else if (role != null) {
            users = userRepository.findByRole(role);
        } else if (department != null) {
            users = userRepository.findByDepartment(department);
        } else {
            users = userRepository.findAll();
        }
        // §10 : un compte desactive n'est pas propose pour de nouvelles
        // utilisations (selects enseignant, affectation RP...). L'admin peut
        // demander explicitement les inactifs (vue [Inactifs]) ou tous (null).
        if (actif != null) {
            boolean wanted = actif;
            users = users.stream().filter(u -> u.isActive() == wanted).toList();
        }
        return sortUsers(users).stream().map(userMapper::toResponseDto).toList();
    }

    public UserResponseDto updateUser(Long id, UserRequestDto request) {
        accessScope.requireAdmin();
        User user = findUserOrThrow(id);
        validateDepartment(request.getRole(), request.getDepartment());

        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Un utilisateur avec l'email " + request.getEmail() + " existe déjà");
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());
        user.setDepartment(request.getDepartment());
        user.setPhoneNumber(request.getPhoneNumber());

        User updatedUser = userRepository.save(user);
        return userMapper.toResponseDto(updatedUser);
    }

    public UserResponseDto changeRole(Long id, Role newRole) {
        accessScope.requireAdmin();
        User user = findUserOrThrow(id);
        user.setRole(newRole);
        User updatedUser = userRepository.save(user);
        return userMapper.toResponseDto(updatedUser);
    }

    public void deactivateUser(Long id) {
        accessScope.requireAdmin();
        User user = findUserOrThrow(id);
        user.setActive(false);
        userRepository.save(user);
        auditService.record(AuditAction.DEACTIVATION, MODULE,
                "Desactivation de l'utilisateur " + fullName(user) + " (" + user.getEmail() + ")");
    }

    public void reactivateUser(Long id) {
        accessScope.requireAdmin();
        User user = findUserOrThrow(id);
        user.setActive(true);
        userRepository.save(user);
        auditService.record(AuditAction.ACTIVATION, MODULE,
                "Activation de l'utilisateur " + fullName(user) + " (" + user.getEmail() + ")");
    }

    /**
     * Compteurs d'impact avant suppression d'un compte : usages metier qui la
     * bloquent (reservations dont le compte est proprietaire, filieres dont il
     * est responsable pedagogique). Sert a la modale de confirmation. Rappel
     * (§ suppression vs desactivation) : pour un compte porteur d'historique, la
     * <b>desactivation</b> est preferable a la suppression — cette derniere n'est
     * possible que pour un compte sans aucun usage metier.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findUserOrThrow(id));
    }

    /**
     * Supprime <b>physiquement</b> un compte utilisateur. La suppression est
     * refusee, avec message metier, tant que le compte porte un usage metier
     * (reservations, responsabilite de filiere) : dans ce cas on <b>desactive</b>
     * (l'historique est conserve). Une fois libere de tout usage, le compte peut
     * etre supprime : ses dependances techniques (jetons, notifications) sont
     * supprimees et ses references historiques (journal d'audit, « importe par »
     * des emplois du temps) sont anonymisees. Le parametre {@code cascade} est
     * sans effet (aucun enfant hierarchique) : il n'existe que pour l'uniformite
     * de l'API de suppression.
     */
    public void deleteUser(Long id, boolean cascade) {
        accessScope.requireAdmin();
        User user = findUserOrThrow(id);
        if (programRepository.existsByResponsableId(id)) {
            throw new com.campusops.exception.BadRequestException(
                    "Impossible de supprimer cet utilisateur : il est encore responsable d'une ou plusieurs filières. "
                            + "Retirez d'abord ces affectations, puis réessayez.");
        }
        DeletionImpact impact = deletionAnalyzer.analyze(user);
        impact.requireConfirmed(cascade);
        String label = fullName(user) + " (" + user.getEmail() + ")";
        deletionExecutor.deleteUserAccount(user);
        auditService.record(AuditAction.DELETE, MODULE,
                "Suppression du compte utilisateur " + label);
    }

    /**
     * Reinitialise le mot de passe d'un compte (action ADMIN) : genere un nouveau
    * mot de passe temporaire, l'encode, force le changement a la prochaine
    * connexion, puis tente l'envoi par e-mail. Le temporaire est retourne une
    * seule fois a l'administrateur pour permettre la recuperation locale.
     */
    public PasswordResetResponseDto resetPassword(Long id) {
        accessScope.requireAdmin();
        User user = findUserOrThrow(id);
        String newTemporaryPassword = passwordService.generateTemporaryPassword();
        user.setPassword(passwordService.encode(newTemporaryPassword));
        user.setMustChangePassword(true);
        userRepository.save(user);
        auditService.record(AuditAction.UPDATE, MODULE,
                "Reinitialisation du mot de passe de " + fullName(user) + " (" + user.getEmail() + ")");

        log.info("[EMAIL] Demande d'envoi d'identifiants (reset admin) pour {}", user.getEmail());
        boolean emailSent = emailService.sendAdminPasswordResetEmail(user, newTemporaryPassword);
        if (!emailSent) {
            alertCredentialsEmailFailure(user);
        }
        if (emailSent) {
            try {
                notificationService.notifyRoles(EnumSet.of(Role.ADMIN), NotificationType.INFO,
                        "Réinitialisation du mot de passe",
                        "Le mot de passe de " + fullName(user) + " (" + user.getEmail()
                                + ") a été réinitialisé et le nouvel email d'identifiants a été envoyé.",
                        null);
            } catch (Exception ex) {
                // La notification ne doit jamais faire échouer la réinitialisation.
                log.warn("Impossible d'envoyer la notification de réinitialisation du mot de passe", ex);
            }
        }

        return PasswordResetResponseDto.builder()
                .email(user.getEmail())
            .temporaryPassword(newTemporaryPassword)
                .emailSent(emailSent)
                .message(emailSent
                        ? "Mot de passe reinitialise : les nouveaux identifiants ont ete envoyes par e-mail."
                : "Mot de passe reinitialise. L'envoi de l'e-mail a echoue ou est desactive : utilisez le mot de passe temporaire affiche pour la connexion.")
                .build();
    }

    /**
     * Renvoie les identifiants d'un compte (action ADMIN). Le mot de passe
     * temporaire d'origine n'etant jamais conserve en clair, un NOUVEAU mot de
     * passe temporaire est genere puis envoye par e-mail. Utile lorsque l'e-mail
     * initial n'a pas pu etre delivre.
     */
    public PasswordResetResponseDto resendCredentials(Long id) {
        return resetPassword(id);
    }

    /** Alerte les administrateurs qu'un e-mail d'identifiants n'a pas pu partir. */
    private void alertCredentialsEmailFailure(User user) {
        try {
            notificationService.notifyRoles(EnumSet.of(Role.ADMIN), NotificationType.WARNING,
                    "Echec d'envoi des identifiants",
                    "L'e-mail d'identifiants destine a " + user.getEmail()
                            + " n'a pas pu etre envoye. Utilisez « Renvoyer les identifiants » une fois le probleme SMTP resolu.",
                    null);
        } catch (Exception ex) {
            // L'alerte ne doit jamais faire echouer l'operation metier.
        }
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Utilisateur introuvable avec l'id " + id));
    }

        private List<User> sortUsers(List<User> users) {
        return users.stream()
            .sorted(Comparator
                .comparing(User::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(User::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(User::getEmail, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
        }

    private void validateDepartment(Role role, String department) {
        String normalized = department == null ? null : department.trim();
        boolean required = role == Role.ENSEIGNANT || role == Role.RESPONSABLE_PEDAGOGIQUE;
        if (required && (normalized == null || normalized.isEmpty())) {
            throw new BadRequestException("Le département est obligatoire pour ce rôle.");
        }
        if (normalized != null && !normalized.isEmpty()
                && !departmentRepository.findByActif(true).stream()
                        .anyMatch(item -> item.getNom().equalsIgnoreCase(normalized))) {
            throw new BadRequestException(
                    "Le département sélectionné est introuvable ou inactif.");
        }
    }

    private String fullName(User user) {
        return user.getFirstName() + " " + user.getLastName();
    }
}