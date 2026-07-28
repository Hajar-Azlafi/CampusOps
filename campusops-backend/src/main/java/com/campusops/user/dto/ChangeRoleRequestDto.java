package com.campusops.user.dto;

import com.campusops.enums.Role;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRoleRequestDto {

    @NotNull(message = "Le nouveau role est obligatoire")
    private Role role;
}