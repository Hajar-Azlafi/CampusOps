package com.campusops.user.mapper;

import com.campusops.user.dto.UserRequestDto;
import com.campusops.user.dto.UserResponseDto;
import com.campusops.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(UserRequestDto dto);

    @Mapping(target = "isActive", source = "active")
    UserResponseDto toResponseDto(User user);
}