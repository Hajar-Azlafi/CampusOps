package com.campusops.typeseance.mapper;

import com.campusops.typeseance.dto.TypeSeanceRequestDto;
import com.campusops.typeseance.dto.TypeSeanceResponseDto;
import com.campusops.typeseance.entity.TypeSeance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TypeSeanceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TypeSeance toEntity(TypeSeanceRequestDto dto);

    TypeSeanceResponseDto toResponseDto(TypeSeance entity);
}
