package com.campusops.user.dto;

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
public class ImportResultDto {
    private int totalRows;
    private int successCount;
    private int errorCount;
    private List<CreatedAccountDto> createdAccounts;
    private List<ImportRowErrorDto> errors;
}