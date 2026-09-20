package com.campusops.schedule.mapper;

import com.campusops.schedule.dto.ScheduleRequestDto;
import com.campusops.schedule.dto.ScheduleResponseDto;
import com.campusops.schedule.entity.Schedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "timeSlot", ignore = true)
    @Mapping(target = "space", ignore = true)
    @Mapping(target = "program", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "promotion", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "semester", ignore = true)
    @Mapping(target = "academicYear", ignore = true)
    // Renseignés par le service : matière/type (dérivés du module et du type de
    // séance configurable), type de présence (défaut PRESENTIEL) et rattachement
    // éventuel à un en-tête d'emploi du temps.
    @Mapping(target = "module", ignore = true)
    @Mapping(target = "typeSeance", ignore = true)
    @Mapping(target = "matiere", ignore = true)
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "emploiDuTemps", ignore = true)
    @Mapping(target = "typePresence", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Schedule toEntity(ScheduleRequestDto dto);

    @Mapping(target = "timeSlotId", source = "timeSlot.id")
    @Mapping(target = "timeSlotHeureDebut", source = "timeSlot.heureDebut")
    @Mapping(target = "timeSlotHeureFin", source = "timeSlot.heureFin")
    @Mapping(target = "spaceId", source = "space.id")
    @Mapping(target = "spaceNom", source = "space.nom")
    @Mapping(target = "spaceCode", source = "space.code")
    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "programNom", source = "program.nom")
    @Mapping(target = "levelId", source = "level.id")
    @Mapping(target = "levelNom", source = "level.nom")
    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionNom", source = "promotion.nom")
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupNom", source = "group.nom")
    @Mapping(target = "semesterId", source = "semester.id")
    @Mapping(target = "semesterNom", source = "semester.nom")
    @Mapping(target = "academicYearId", source = "academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "academicYear.libelle")
    @Mapping(target = "moduleId", source = "module.id")
    @Mapping(target = "moduleNom", source = "module.nom")
    @Mapping(target = "typeSeanceId", source = "typeSeance.id")
    @Mapping(target = "typeSeanceNom", source = "typeSeance.nom")
    @Mapping(target = "typeSeanceCouleur", source = "typeSeance.couleur")
    @Mapping(target = "emploiDuTempsId", source = "emploiDuTemps.id")
    ScheduleResponseDto toResponseDto(Schedule schedule);
}
