package com.campusops.space.mapper;

import com.campusops.space.dto.SpaceRequestDto;
import com.campusops.space.dto.SpaceResponseDto;
import com.campusops.space.entity.Space;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SpaceMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "reserve", ignore = true)
    @Mapping(target = "floor", ignore = true)
    @Mapping(target = "equipments", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Space toEntity(SpaceRequestDto dto);

    @Mapping(target = "floorId", source = "floor.id")
    @Mapping(target = "floorNom", source = "floor.nom")
    @Mapping(target = "floorNumero", source = "floor.numero")
    @Mapping(target = "buildingId", source = "floor.building.id")
    @Mapping(target = "buildingNom", source = "floor.building.nom")
    @Mapping(target = "buildingCode", source = "floor.building.code")
    SpaceResponseDto toResponseDto(Space space);
}
