package com.campusops.timetable.mapper;

import com.campusops.timetable.dto.EmploiDuTempsResponseDto;
import com.campusops.timetable.entity.EmploiDuTemps;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EmploiDuTempsMapper {

    @Mapping(target = "academicYearId", source = "academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "academicYear.libelle")
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
    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "sessionNom", source = "session.nom")
    @Mapping(target = "importeParId", source = "importePar.id")
    // Renseignes par le service (concatenation du nom, comptage des seances,
    // calcul de l'expiration a la date du jour). dateDebut/dateFin sont mappes
    // automatiquement (memes noms).
    @Mapping(target = "importeParNom", ignore = true)
    @Mapping(target = "nombreSeances", ignore = true)
    @Mapping(target = "expire", ignore = true)
    EmploiDuTempsResponseDto toResponseDto(EmploiDuTemps entity);
}
