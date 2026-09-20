package com.campusops.building.mapper;

import com.campusops.building.dto.BuildingRequestDto;
import com.campusops.building.dto.BuildingResponseDto;
import com.campusops.building.entity.Building;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BuildingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Building toEntity(BuildingRequestDto dto);

    BuildingResponseDto toResponseDto(Building building);
}
