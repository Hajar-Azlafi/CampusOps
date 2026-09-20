package com.campusops.academicsession.service;

import com.campusops.academicsession.dto.SessionUniversitaireRequestDto;
import com.campusops.academicsession.dto.SessionUniversitaireResponseDto;
import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.academicsession.mapper.SessionUniversitaireMapper;
import com.campusops.academicsession.repository.SessionUniversitaireRepository;
import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des sessions universitaires (session normale, rattrapage, automne,
 * printemps, ...). Concept CONFIGURABLE (cahier des charges §4), distinct du
 * « type de seance » (Cours/TD/TP). La configuration reste reservee a
 * l'administrateur ; la consultation est ouverte aux utilisateurs authentifies
 * (un responsable pedagogique doit pouvoir choisir une session).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SessionUniversitaireService {

    private final SessionUniversitaireRepository sessionRepository;
    private final SessionUniversitaireMapper sessionMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public SessionUniversitaireResponseDto createSession(SessionUniversitaireRequestDto request) {
        accessScope.requireAdmin();
        String code = normalizeCode(request.getCode());
        if (sessionRepository.existsByCode(code)) {
            throw new DuplicateResourceException(
                    "Une session avec le code '" + code + "' existe déjà");
        }
        SessionUniversitaire entity = sessionMapper.toEntity(request);
        entity.setNom(request.getNom().trim());
        entity.setCode(code);
        entity.setActif(true);
        return sessionMapper.toResponseDto(sessionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public SessionUniversitaireResponseDto getSessionById(Long id) {
        return sessionMapper.toResponseDto(findSessionOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<SessionUniversitaireResponseDto> filterSessions(Boolean actif) {
        List<SessionUniversitaire> sessions = (actif != null)
                ? sessionRepository.findByActif(actif)
                : sessionRepository.findAllByOrderByOrdreAsc();
        return sessions.stream().map(sessionMapper::toResponseDto).toList();
    }

    public SessionUniversitaireResponseDto updateSession(Long id, SessionUniversitaireRequestDto request) {
        accessScope.requireAdmin();
        SessionUniversitaire entity = findSessionOrThrow(id);
        String code = normalizeCode(request.getCode());
        sessionRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Une session avec le code '" + code + "' existe déjà");
                });
        entity.setNom(request.getNom().trim());
        entity.setCode(code);
        entity.setOrdre(request.getOrdre());
        return sessionMapper.toResponseDto(sessionRepository.save(entity));
    }

    public void deactivateSession(Long id) {
        accessScope.requireAdmin();
        SessionUniversitaire entity = findSessionOrThrow(id);
        entity.setActif(false);
        sessionRepository.save(entity);
    }

    public void activateSession(Long id) {
        accessScope.requireAdmin();
        SessionUniversitaire entity = findSessionOrThrow(id);
        entity.setActif(true);
        sessionRepository.save(entity);
    }

    /**
     * Compteurs d'impact avant suppression d'une session universitaire : usages
     * métier qui la bloquent (emplois du temps et examens rattachés à cette
     * session). Sert à la modale de confirmation. Préférer la <b>désactivation</b>
     * pour retirer une session de la saisie sans toucher à l'historique.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findSessionOrThrow(id));
    }

    /**
     * Supprime une session universitaire. Référentiel : aucun enfant en cascade.
     * La suppression est refusée, avec message métier, tant qu'un emploi du temps
     * ou un examen s'y rattache : il faut d'abord libérer ces usages
     * (« suppression après modification »). Le paramètre {@code cascade} est sans
     * effet (aucun enfant) : il n'existe que pour l'uniformité de l'API.
     */
    public void deleteSession(Long id, boolean cascade) {
        accessScope.requireAdmin();
        SessionUniversitaire entity = findSessionOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(entity);
        impact.requireConfirmed(cascade);
        sessionRepository.delete(entity);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code de la session est obligatoire");
        }
        return code.trim().toUpperCase();
    }

    private SessionUniversitaire findSessionOrThrow(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Session universitaire introuvable avec l'id " + id));
    }
}
