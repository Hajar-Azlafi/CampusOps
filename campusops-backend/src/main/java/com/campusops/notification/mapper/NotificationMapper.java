package com.campusops.notification.mapper;

import com.campusops.notification.dto.NotificationResponseDto;
import com.campusops.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "userId", source = "user.id")
    NotificationResponseDto toResponseDto(Notification notification);
}
