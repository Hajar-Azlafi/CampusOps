package com.campusops.exam.mapper;

import com.campusops.exam.dto.ExamenResponseDto;
import com.campusops.exam.entity.Examen;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExamenMapper {

    @Mapping(target = "timeSlotId", source = "timeSlot.id")
    @Mapping(target = "timeSlotNom", source = "timeSlot.nom")
    @Mapping(target = "timeSlotHeureDebut", source = "timeSlot.heureDebut")
    @Mapping(target = "timeSlotHeureFin", source = "timeSlot.heureFin")
    @Mapping(target = "spaceId", source = "space.id")
    @Mapping(target = "spaceNom", source = "space.nom")
    @Mapping(target = "spaceCode", source = "space.code")
    @Mapping(target = "moduleId", source = "module.id")
    @Mapping(target = "moduleNom", source = "module.nom")
    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "programNom", source = "program.nom")
    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionNom", source = "promotion.nom")
    // group nullable (examen sur toute la promotion) : MapStruct gère le null.
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "groupNom", source = "group.nom")
    @Mapping(target = "semesterId", source = "semester.id")
    @Mapping(target = "semesterNom", source = "semester.nom")
    @Mapping(target = "academicYearId", source = "academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "academicYear.libelle")
    @Mapping(target = "sessionId", source = "session.id")
    @Mapping(target = "sessionNom", source = "session.nom")
    @Mapping(target = "sessionCode", source = "session.code")
    ExamenResponseDto toResponseDto(Examen entity);
}
