package com.campusops.schedule.dto;

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
public class ScheduleImportResultDto {

    private String fileName;
    private int totalRows;
    private int successCount;
    private int errorCount;
    private List<String> createdEntities;
    private List<ScheduleImportRowErrorDto> errors;
}
