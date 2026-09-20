package com.campusops.calendar.service;

import com.campusops.calendar.dto.NonWorkingDayRequestDto;
import com.campusops.calendar.dto.NonWorkingDayResponseDto;
import com.campusops.calendar.entity.NonWorkingDay;
import com.campusops.calendar.mapper.NonWorkingDayMapper;
import com.campusops.calendar.repository.NonWorkingDayRepository;
import com.campusops.enums.NonWorkingDayType;
import com.campusops.exception.BadRequestException;
import com.campusops.exception.ResourceNotFoundException;
import com.campusops.security.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Administration du calendrier non ouvrable (§4-§5).
 *
 * <p>Configuration reservee a l'ADMIN (creation, modification, activation,
 * suppression). La consultation reste ouverte aux utilisateurs authentifies :
 * l'interface de recherche doit pouvoir expliquer pourquoi une date est
 * fermee.</p>
 *
 * <p>Le calendrier est generique : aucune date n'est figee dans le code. Les
 * fetes religieuses sont enregistrees comme previsionnelles et se corrigent
 * d'un simple {@code PUT} quand la date officielle est annoncee.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class NonWorkingDayService {

    private final NonWorkingDayRepository repository;
    private final NonWorkingDayMapper mapper;
    private final AccessScopeService accessScope;

    public NonWorkingDayResponseDto create(NonWorkingDayRequestDto request) {
        accessScope.requireAdmin();
        LocalDate debut = request.getDateDebut();
        LocalDate fin = normalizeFin(debut, request.getDateFin());

        NonWorkingDay entity = NonWorkingDay.builder()
                .dateDebut(debut)
                .dateFin(fin)
                .libelle(normalizeLibelle(request.getLibelle()))
                .type(request.getType() != null ? request.getType() : NonWorkingDayType.PERSONNALISE)
                .previsionnel(Boolean.TRUE.equals(request.getPrevisionnel()))
            .recurrent(isRecurringHoliday(request.getType(), request.getRecurrent()))
                .commentaire(trimToNull(request.getCommentaire()))
                .actif(true)
                .build();

        return mapper.toResponseDto(repository.save(entity));
    }

    public NonWorkingDayResponseDto update(Long id, NonWorkingDayRequestDto request) {
        accessScope.requireAdmin();
        NonWorkingDay entity = findOrThrow(id);

        LocalDate debut = request.getDateDebut();
        LocalDate fin = normalizeFin(debut, request.getDateFin());

        entity.setDateDebut(debut);
        entity.setDateFin(fin);
        entity.setLibelle(normalizeLibelle(request.getLibelle()));
        if (request.getType() != null) {
            entity.setType(request.getType());
        }
        if (request.getPrevisionnel() != null) {
            entity.setPrevisionnel(request.getPrevisionnel());
        }
        NonWorkingDayType type = request.getType() != null ? request.getType() : entity.getType();
        entity.setRecurrent(isRecurringHoliday(type, request.getRecurrent()));
        entity.setCommentaire(trimToNull(request.getCommentaire()));

        return mapper.toResponseDto(repository.save(entity));
    }

    public void activate(Long id) {
        accessScope.requireAdmin();
        NonWorkingDay entity = findOrThrow(id);
        entity.setActif(true);
        repository.save(entity);
    }

    /** Desactivation non destructive : la journee redevient ouvrable. */
    public void deactivate(Long id) {
        accessScope.requireAdmin();
        NonWorkingDay entity = findOrThrow(id);
        entity.setActif(false);
        repository.save(entity);
    }

    public void delete(Long id) {
        accessScope.requireAdmin();
        repository.delete(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public NonWorkingDayResponseDto getById(Long id) {
        return mapper.toResponseDto(findOrThrow(id));
    }

    /**
     * Liste filtrable : par statut et/ou par periode. Sans filtre, renvoie tout
     * le calendrier trie chronologiquement.
     */
    @Transactional(readOnly = true)
    public List<NonWorkingDayResponseDto> list(Boolean actif, LocalDate debut, LocalDate fin) {
        List<NonWorkingDay> entities;
        if (debut != null && fin != null) {
            if (fin.isBefore(debut)) {
                throw new BadRequestException(
                        "La date de fin doit être postérieure ou égale à la date de début");
            }
            entities = Boolean.TRUE.equals(actif)
                    ? repository.findActiveOverlapping(debut, fin)
                    : repository.findOverlapping(debut, fin);
            if (Boolean.FALSE.equals(actif)) {
                entities = entities.stream().filter(n -> !n.isActif()).toList();
            }
        } else if (actif != null) {
            entities = repository.findByActifOrderByDateDebutAsc(actif);
        } else {
            entities = repository.findAllByOrderByDateDebutAsc();
        }
        return entities.stream().map(mapper::toResponseDto).toList();
    }

    /** Annee civile complete : utile a l'ecran d'administration du calendrier. */
    @Transactional(readOnly = true)
    public List<NonWorkingDayResponseDto> listByYear(int annee) {
        return list(null, LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31));
    }

    // ----- Helpers -----

    private NonWorkingDay findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(
                "Journée non ouvrable introuvable avec l'identifiant : " + id));
    }

    private LocalDate normalizeFin(LocalDate debut, LocalDate fin) {
        if (debut == null) {
            throw new BadRequestException("La date de début est obligatoire");
        }
        if (fin == null) {
            return debut;
        }
        if (fin.isBefore(debut)) {
            throw new BadRequestException(
                    "La date de fin doit être postérieure ou égale à la date de début");
        }
        return fin;
    }

    private boolean isRecurringHoliday(NonWorkingDayType type, Boolean recurrent) {
        if (type == NonWorkingDayType.FERIE_NATIONAL) {
            return true;
        }
        return Boolean.TRUE.equals(recurrent);
    }

    private String normalizeLibelle(String libelle) {
        String value = trimToNull(libelle);
        if (value == null) {
            throw new BadRequestException("Le libellé est obligatoire");
        }
        return value.length() > 150 ? value.substring(0, 150) : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
