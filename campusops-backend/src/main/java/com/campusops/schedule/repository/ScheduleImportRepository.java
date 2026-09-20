package com.campusops.schedule.repository;

import com.campusops.schedule.entity.ScheduleImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleImportRepository extends JpaRepository<ScheduleImport, Long> {

    List<ScheduleImport> findAllByOrderByImportedAtDesc();
}
