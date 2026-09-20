package com.campusops.timeslot.service;

import com.campusops.deletion.DeletionAnalyzer;
import com.campusops.deletion.DeletionImpact;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.DuplicateResourceException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import com.campusops.timeslot.dto.TimeSlotRequestDto;
import com.campusops.timeslot.dto.TimeSlotResponseDto;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.mapper.TimeSlotMapper;
import com.campusops.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des créneaux horaires de référence de l'université (cahier des
 * charges §1–§4). Les créneaux définis ici deviennent la référence utilisée
 * partout (emplois du temps, ajout/modification de séance, disponibilités,
 * filtres). L'objectif est d'éviter la saisie libre d'heures.
 *
 * <p><b>Configuration réservée à l'ADMIN</b> (via {@link AccessScopeService}) :
 * création, modification, (dés)activation et réorganisation. La consultation
 * reste ouverte aux utilisateurs authentifiés (un responsable pédagogique doit
 * pouvoir choisir un créneau existant). Les listes sont toujours renvoyées
 * triées par {@code ordre} croissant (§3), jamais dans un ordre aléatoire.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final TimeSlotMapper timeSlotMapper;
    private final AccessScopeService accessScope;
    private final DeletionAnalyzer deletionAnalyzer;

    public TimeSlotResponseDto createTimeSlot(TimeSlotRequestDto request) {
        accessScope.requireAdmin();
        validateHours(request);
        if (timeSlotRepository.existsByHeureDebutAndHeureFin(
                request.getHeureDebut(), request.getHeureFin())) {
            throw new DuplicateResourceException(
                    "Un créneau horaire identique existe déjà");
        }

        TimeSlot timeSlot = timeSlotMapper.toEntity(request);
        timeSlot.setNom(normalizeNom(request.getNom()));
        timeSlot.setActif(true);
        timeSlot.setOrdre(resolveOrdre(request.getOrdre()));

        TimeSlot saved = timeSlotRepository.save(timeSlot);
        return timeSlotMapper.toResponseDto(saved);
    }

    @Transactional(readOnly = true)
    public TimeSlotResponseDto getTimeSlotById(Long id) {
        return timeSlotMapper.toResponseDto(findTimeSlotOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<TimeSlotResponseDto> filterTimeSlots(Boolean actif) {
        // Toujours trié par ordre chronologique (§3), jamais aléatoire.
        List<TimeSlot> timeSlots = (actif != null)
                ? timeSlotRepository.findByActifOrderByOrdreAscHeureDebutAsc(actif)
                : timeSlotRepository.findAllByOrderByOrdreAscHeureDebutAsc();
        return timeSlots.stream().map(timeSlotMapper::toResponseDto).toList();
    }

    public TimeSlotResponseDto updateTimeSlot(Long id, TimeSlotRequestDto request) {
        accessScope.requireAdmin();
        TimeSlot timeSlot = findTimeSlotOrThrow(id);
        validateHours(request);

        timeSlotRepository.findByHeureDebutAndHeureFin(
                        request.getHeureDebut(), request.getHeureFin())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "Un créneau horaire identique existe déjà");
                });

        timeSlot.setNom(normalizeNom(request.getNom()));
        timeSlot.setHeureDebut(request.getHeureDebut());
        timeSlot.setHeureFin(request.getHeureFin());
        if (request.getOrdre() != null) {
            timeSlot.setOrdre(request.getOrdre());
        } else if (timeSlot.getOrdre() == null) {
            timeSlot.setOrdre(resolveOrdre(null));
        }

        TimeSlot updated = timeSlotRepository.save(timeSlot);
        return timeSlotMapper.toResponseDto(updated);
    }

    /**
     * Réorganise les créneaux : la liste ordonnée d'identifiants reçue fixe
     * l'ordre d'affichage (1, 2, 3, ...). Les identifiants inconnus sont
     * ignorés ; les créneaux non cités conservent leur ordre courant.
     */
    public List<TimeSlotResponseDto> reorder(List<Long> orderedIds) {
        accessScope.requireAdmin();
        if (orderedIds == null || orderedIds.isEmpty()) {
            throw new BadRequestException("La liste des créneaux à réorganiser est vide");
        }
        int ordre = 1;
        for (Long id : orderedIds) {
            TimeSlot slot = timeSlotRepository.findById(id).orElse(null);
            if (slot != null) {
                slot.setOrdre(ordre++);
                timeSlotRepository.save(slot);
            }
        }
        return timeSlotRepository.findAllByOrderByOrdreAscHeureDebutAsc().stream()
                .map(timeSlotMapper::toResponseDto).toList();
    }

    public void deactivateTimeSlot(Long id) {
        accessScope.requireAdmin();
        TimeSlot timeSlot = findTimeSlotOrThrow(id);
        timeSlot.setActif(false);
        timeSlotRepository.save(timeSlot);
    }

    public void activateTimeSlot(Long id) {
        accessScope.requireAdmin();
        TimeSlot timeSlot = findTimeSlotOrThrow(id);
        timeSlot.setActif(true);
        timeSlotRepository.save(timeSlot);
    }

    /**
     * Compteurs d'impact avant suppression d'un créneau horaire : usages métier
     * qui la bloquent (séances et examens positionnés sur ce créneau). Sert à la
     * modale de confirmation. Préférer la <b>désactivation</b> pour retirer un
     * créneau de la saisie tout en préservant l'historique.
     */
    @Transactional(readOnly = true)
    public DeletionImpact getDeletionImpact(Long id) {
        accessScope.requireAdmin();
        return deletionAnalyzer.analyze(findTimeSlotOrThrow(id));
    }

    /**
     * Supprime un créneau horaire. Référentiel : aucun enfant en cascade. La
     * suppression est refusée, avec message métier, tant qu'une séance ou un
     * examen s'appuie sur ce créneau : il faut d'abord libérer ces usages
     * (« suppression après modification »). Le paramètre {@code cascade} est sans
     * effet (aucun enfant) : il n'existe que pour l'uniformité de l'API.
     */
    public void deleteTimeSlot(Long id, boolean cascade) {
        accessScope.requireAdmin();
        TimeSlot timeSlot = findTimeSlotOrThrow(id);
        DeletionImpact impact = deletionAnalyzer.analyze(timeSlot);
        impact.requireConfirmed(cascade);
        timeSlotRepository.delete(timeSlot);
    }

    // ----- Helpers -----

    private void validateHours(TimeSlotRequestDto request) {
        if (!request.getHeureFin().isAfter(request.getHeureDebut())) {
            throw new BadRequestException(
                    "L'heure de fin doit etre posterieure a l'heure de debut");
        }
    }

    private String normalizeNom(String nom) {
        if (nom == null) {
            return null;
        }
        String trimmed = nom.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Ordre fourni s'il est non nul, sinon dernier + 1 (fin de liste). */
    private int resolveOrdre(Integer requested) {
        if (requested != null) {
            return requested;
        }
        return timeSlotRepository.findAll().stream()
                .map(TimeSlot::getOrdre)
                .filter(o -> o != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private TimeSlot findTimeSlotOrThrow(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Créneau horaire introuvable avec l'id " + id));
    }
}
