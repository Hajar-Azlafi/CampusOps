package com.campusops.level.mapper;

import com.campusops.level.dto.LevelRequestDto;
import com.campusops.level.dto.LevelResponseDto;
import com.campusops.level.entity.Level;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface LevelMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "actif", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Level toEntity(LevelRequestDto dto);

    LevelResponseDto toResponseDto(Level level);
}
