package com.campusops.promotion.mapper;

import com.campusops.promotion.dto.PromotionRequestDto;
import com.campusops.promotion.dto.PromotionResponseDto;
import com.campusops.promotion.entity.Promotion;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PromotionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "program", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "academicYear", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Promotion toEntity(PromotionRequestDto dto);

    @Mapping(target = "programId", source = "program.id")
    @Mapping(target = "programNom", source = "program.nom")
    @Mapping(target = "levelId", source = "level.id")
    @Mapping(target = "levelNom", source = "level.nom")
    @Mapping(target = "levelNombreAnnees", source = "level.nombreAnnees")
    @Mapping(target = "academicYearId", source = "academicYear.id")
    @Mapping(target = "academicYearLibelle", source = "academicYear.libelle")
    PromotionResponseDto toResponseDto(Promotion promotion);
}
