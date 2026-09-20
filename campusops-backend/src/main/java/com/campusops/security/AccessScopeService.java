package com.campusops.security;

import com.campusops.enums.Role;
import com.campusops.program.entity.Program;
import com.campusops.program.repository.ProgramRepository;
import com.campusops.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service central d'application du perimetre (RBAC) cote backend.
 *
 * <p>La securite ne repose PAS uniquement sur le masquage des boutons cote
 * React : chaque service academique interroge ce composant pour verifier, a
 * partir de l'utilisateur authentifie (le principal JWT est l'entite
 * {@link User}), que l'action demandee reste dans son perimetre. Meme en cas
 * de manipulation d'URL ou d'appel direct a l'API, un responsable pedagogique
 * ne peut atteindre que les donnees de SES filieres.</p>
 *
 * <p>Modele : une filiere ({@link Program}) porte un {@code responsable}
 * (relation ManyToOne). Le perimetre d'un responsable pedagogique est donc
 * l'ensemble des filieres dont il est responsable. L'ADMIN n'est pas borne.</p>
 */
@Service
@RequiredArgsConstructor
public class AccessScopeService {

    private final ProgramRepository programRepository;

    /** Utilisateur authentifie courant (principal JWT = entite User). */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Utilisateur non authentifié.");
        }
        return user;
    }

    public boolean isAdmin() {
        return getCurrentUser().getRole() == Role.ADMIN;
    }

    public boolean isResponsablePedagogique() {
        return getCurrentUser().getRole() == Role.RESPONSABLE_PEDAGOGIQUE;
    }

    /** Exige le role ADMIN, sinon 403. Reserve les actions globales/structure. */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Action réservée à l'administrateur.");
        }
    }

    /**
     * Identifiants des filieres gerees par l'utilisateur courant.
     * ADMIN : renvoie {@code null} (perimetre non borne, tout est accessible).
     * RESPONSABLE_PEDAGOGIQUE : ses filieres (liste, potentiellement vide).
     * Autres roles : liste vide (aucune filiere geree).
     */
    @Transactional(readOnly = true)
    public List<Long> myProgramIds() {
        User me = getCurrentUser();
        if (me.getRole() == Role.ADMIN) {
            return null;
        }
        if (me.getRole() != Role.RESPONSABLE_PEDAGOGIQUE) {
            return List.of();
        }
        return programRepository.findByResponsableId(me.getId()).stream()
                .map(Program::getId)
                .collect(Collectors.toList());
    }

    /** Ensemble des filieres gerees par le RP courant (vide si aucune). */
    @Transactional(readOnly = true)
    public Set<Long> myProgramIdSet() {
        List<Long> ids = myProgramIds();
        return ids == null ? Set.of() : new HashSet<>(ids);
    }

    /**
     * Vrai si l'utilisateur courant peut acceder aux donnees de la filiere.
     * ADMIN : toujours. RP : uniquement ses filieres. Autres : jamais.
     */
    @Transactional(readOnly = true)
    public boolean canAccessProgram(Long programId) {
        if (isAdmin()) {
            return true;
        }
        return programId != null
                && isResponsablePedagogique()
                && programRepository.existsByIdAndResponsableId(programId, getCurrentUser().getId());
    }

    /** Verifie l'acces a la filiere, sinon 403. */
    @Transactional(readOnly = true)
    public void assertProgramAccessible(Long programId) {
        if (!canAccessProgram(programId)) {
            throw new AccessDeniedException("Accès refusé : cette filière ne fait pas partie de votre périmètre.");
        }
    }
}
