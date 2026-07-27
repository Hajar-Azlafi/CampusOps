package com.campusops.auth.dto;

import com.campusops.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDto {

    private String token;
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
}