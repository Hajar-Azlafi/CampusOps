package com.campusops.group.mapper;

import com.campusops.group.dto.GroupRequestDto;
import com.campusops.group.dto.GroupResponseDto;
import com.campusops.group.entity.Group;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "promotion", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Group toEntity(GroupRequestDto dto);

    @Mapping(target = "promotionId", source = "promotion.id")
    @Mapping(target = "promotionNom", source = "promotion.nom")
    @Mapping(target = "programId", source = "promotion.program.id")
    @Mapping(target = "programNom", source = "promotion.program.nom")
    @Mapping(target = "levelId", source = "promotion.level.id")
    @Mapping(target = "levelNom", source = "promotion.level.nom")
    @Mapping(target = "levelNombreAnnees", source = "promotion.level.nombreAnnees")
    @Mapping(target = "academicYearId", source = "promotion.academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "promotion.academicYear.libelle")
    GroupResponseDto toResponseDto(Group group);
}
