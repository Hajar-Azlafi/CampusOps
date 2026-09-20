package com.campusops.calendar.mapper;

import com.campusops.calendar.dto.NonWorkingDayResponseDto;
import com.campusops.calendar.entity.NonWorkingDay;
import com.campusops.enums.NonWorkingDayType;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

/**
 * Mapper manuel : le DTO expose des champs derives (libelle lisible du type,
 * nombre de jours fermes) qui ne se deduisent pas d'une simple projection.
 */
@Component
public class NonWorkingDayMapper {

    public NonWorkingDayResponseDto toResponseDto(NonWorkingDay entity) {
        if (entity == null) {
            return null;
        }
        int jours = 1;
        if (entity.getDateDebut() != null && entity.getDateFin() != null) {
            jours = (int) ChronoUnit.DAYS.between(entity.getDateDebut(), entity.getDateFin()) + 1;
        }
        return NonWorkingDayResponseDto.builder()
                .id(entity.getId())
                .dateDebut(entity.getDateDebut())
                .dateFin(entity.getDateFin())
                .libelle(entity.getLibelle())
                .type(entity.getType())
                .typeLibelle(typeLibelle(entity.getType()))
                .previsionnel(entity.isPrevisionnel())
                .recurrent(entity.isRecurrent())
                .commentaire(entity.getCommentaire())
                .actif(entity.isActif())
                .nombreJours(Math.max(jours, 1))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public String typeLibelle(NonWorkingDayType type) {
        if (type == null) {
            return "Jour non ouvrable";
        }
        return switch (type) {
            case FERIE_NATIONAL -> "Jour férié national";
            case FERIE_RELIGIEUX -> "Fête religieuse";
            case VACANCES -> "Vacances universitaires";
            case FERMETURE_EXCEPTIONNELLE -> "Fermeture exceptionnelle";
            case PERSONNALISE -> "Jour non ouvrable";
        };
    }
}
