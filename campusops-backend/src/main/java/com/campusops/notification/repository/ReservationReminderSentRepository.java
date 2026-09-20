package com.campusops.notification.repository;

import com.campusops.notification.entity.ReservationReminderSent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReservationReminderSentRepository extends JpaRepository<ReservationReminderSent, Long> {

    boolean existsByReservationIdAndReminderKey(Long reservationId, String reminderKey);

    Optional<ReservationReminderSent> findByReservationIdAndReminderKey(Long reservationId, String reminderKey);
}
