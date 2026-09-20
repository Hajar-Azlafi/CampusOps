package com.campusops.equipment.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignEquipmentsRequestDto {

    @NotEmpty(message = "La liste des équipements est obligatoire")
    private List<Long> equipmentIds;
}
