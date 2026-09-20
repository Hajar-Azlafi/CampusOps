package com.campusops.floor.mapper;

import com.campusops.floor.dto.FloorRequestDto;
import com.campusops.floor.dto.FloorResponseDto;
import com.campusops.floor.entity.Floor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FloorMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "building", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Floor toEntity(FloorRequestDto dto);

    @Mapping(target = "buildingId", source = "building.id")
    @Mapping(target = "buildingNom", source = "building.nom")
    @Mapping(target = "buildingCode", source = "building.code")
    FloorResponseDto toResponseDto(Floor floor);
}
