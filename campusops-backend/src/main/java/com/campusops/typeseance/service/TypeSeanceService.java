package com.campusops.typeseance.service;

import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import com.campusops.typeseance.dto.TypeSeanceRequestDto;
import com.campusops.typeseance.dto.TypeSeanceResponseDto;
import com.campusops.typeseance.entity.TypeSeance;
import com.campusops.typeseance.mapper.TypeSeanceMapper;
import com.campusops.typeseance.repository.TypeSeanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des <b>types de séance configurables</b> (cahier des charges §7) :
 * Cours, TD, TP, Examen, Contrôle, Soutenance, Autre — extensibles par
 * l'administrateur. Concept distinct du type de présence (§7) et de la session
 * universitaire (§4).
 *
 * <p><b>Configuration réservée à l'ADMIN</b> (via {@link AccessScopeService}) :
 * création, modification et (dés)activation. La consultation reste ouverte aux
 * utilisateurs authentifiés (un responsable pédagogique doit pouvoir choisir un
 * type existant). Les listes sont toujours renvoyées triées par {@code ordre}
 * croissant (§3), jamais dans un ordre aléatoire.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TypeSeanceService {

    private final TypeSeanceRepository typeSeanceRepository;
    private final TypeSeanceMapper typeSeanceMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public TypeSeanceResponseDto createTypeSeance(TypeSeanceRequestDto request) {
        accessScope.requireAdmin();
        String code = normalizeCode(request.getCode());
        if (typeSeanceRepository.existsByCode(code)) {
            throw new DuplicateResourceException(
                    "Un type de séance avec le code '" + code + "' existe déjà");
        }
        TypeSeance entity = typeSeanceMapper.toEntity(request);
        entity.setNom(request.getNom().trim());
        entity.setCode(code);
        entity.setCouleur(normalizeCouleur(request.getCouleur()));
        entity.setActif(true);
        return typeSeanceMapper.toResponseDto(typeSeanceRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public TypeSeanceResponseDto getTypeSeanceById(Long id) {
        return typeSeanceMapper.toResponseDto(findTypeSeanceOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TypeSeanceResponseDto> filterTypesSeance(Boolean actif) {
        // Toujours trié par ordre chronologique (§3), jamais aléatoire.
        List<TypeSeance> types = (actif != null)
                ? typeSeanceRepository.findByActifOrderByOrdreAsc(actif)
                : typeSeanceRepository.findAllByOrderByOrdreAsc();
        return types.stream().map(typeSeanceMapper::toResponseDto).toList();
    }

    public TypeSeanceResponseDto updateTypeSeance(Long id, TypeSeanceRequestDto request) {
        accessScope.requireAdmin();
        TypeSeance entity = findTypeSeanceOrThrow(id);
        String code = normalizeCode(request.getCode());
        typeSeanceRepository.findByCode(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Un type de séance avec le code '" + code + "' existe déjà");
                });
        entity.setNom(request.getNom().trim());
        entity.setCode(code);
        entity.setCouleur(normalizeCouleur(request.getCouleur()));
        entity.setOrdre(request.getOrdre());
        return typeSeanceMapper.toResponseDto(typeSeanceRepository.save(entity));
    }

    public void deactivateTypeSeance(Long id) {
        accessScope.requireAdmin();
        TypeSeance entity = findTypeSeanceOrThrow(id);
        entity.setActif(false);
        typeSeanceRepository.save(entity);
    }

    public void activateTypeSeance(Long id) {
        accessScope.requireAdmin();
        TypeSeance entity = findTypeSeanceOrThrow(id);
        entity.setActif(true);
        typeSeanceRepository.save(entity);
    }

    /**
     * Compteurs d'impact avant suppression d'un type de séance : usage métier qui
     * la bloque (séances de ce type). Sert à la modale de confirmation. Préférer
     * la <b>désactivation</b> pour retirer un type de la saisie sans toucher à
     * l'historique.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findTypeSeanceOrThrow(id));
    }

    /**
     * Supprime un type de séance. Référentiel : aucun enfant en cascade. La
     * suppression est refusée, avec message métier, tant qu'une séance porte ce
     * type : il faut d'abord réaffecter ou supprimer ces séances (« suppression
     * après modification »). Le paramètre {@code cascade} est sans effet (aucun
     * enfant) : il n'existe que pour l'uniformité de l'API.
     */
    public void deleteTypeSeance(Long id, boolean cascade) {
        accessScope.requireAdmin();
        TypeSeance entity = findTypeSeanceOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(entity);
        impact.requireConfirmed(cascade);
        typeSeanceRepository.delete(entity);
    }

    // ----- Helpers -----

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code du type de séance est obligatoire");
        }
        return code.trim().toUpperCase();
    }

    /** Couleur hexadécimale normalisée en majuscules, ou {@code null} si absente. */
    private String normalizeCouleur(String couleur) {
        if (couleur == null || couleur.isBlank()) {
            return null;
        }
        return couleur.trim().toUpperCase();
    }

    private TypeSeance findTypeSeanceOrThrow(Long id) {
        return typeSeanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Type de séance introuvable avec l'id " + id));
    }
}
