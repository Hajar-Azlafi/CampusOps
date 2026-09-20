package com.campusops.config;

import com.campusops.exam.repository.ExamenRepository;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Removes the four legacy time slots that followed the reference grid. */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(12)
public class LegacyTimeSlotCleanupInitializer implements CommandLineRunner {

    private final TimeSlotRepository timeSlotRepository;
    private final ScheduleRepository scheduleRepository;
    private final ExamenRepository examenRepository;

    @Value("${campusops.seed.cleanup-legacy-time-slots:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Nettoyage des anciens creneaux desactive.");
            return;
        }

        List<TimeSlot> legacySlots = timeSlotRepository.findAll().stream()
                .filter(slot -> slot.getOrdre() != null
                        && slot.getOrdre() >= 5
                        && slot.getOrdre() <= 8)
                .toList();
        int deleted = 0;
        int skipped = 0;

        for (TimeSlot slot : legacySlots) {
            if (scheduleRepository.existsByTimeSlotId(slot.getId())
                    || examenRepository.existsByTimeSlotId(slot.getId())) {
                skipped++;
                log.warn("Ancien creneau {} conserve : il est encore reference.", slot.getId());
                continue;
            }
            timeSlotRepository.delete(slot);
            deleted++;
        }

        log.info("Nettoyage des anciens creneaux : {} supprime(s), {} conserve(s).",
                deleted, skipped);
    }
}