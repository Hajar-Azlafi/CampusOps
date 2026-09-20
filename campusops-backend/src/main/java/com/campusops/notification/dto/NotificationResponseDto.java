package com.campusops.notification.dto;

import com.campusops.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
)
public class NotificationResponseDto {

    private Long id;

    private Long userId;

    private String titre;
    private String message;
    private NotificationType type;
    private boolean lue;
    private String lien;

    private LocalDateTime createdAt;
}
