package com.campusops.notification.service;

import com.campusops.enums.NotificationType;
import com.campusops.enums.ReminderFrequency;
import com.campusops.enums.ReservationStatus;
import com.campusops.notification.repository.ReservationReminderSentRepository;
import com.campusops.reservation.entity.Reservation;
import com.campusops.reservation.repository.ReservationRepository;
import com.campusops.settings.entity.AppSettings;
import com.campusops.settings.service.SettingsService;
import com.campusops.space.entity.Space;
import com.campusops.user.entity.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReminderServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private ReservationReminderSentRepository reminderSentRepository;

    @InjectMocks
    private ReminderService reminderService;

    private AppSettings settings;

    @BeforeEach
    void setUp() {
        settings = AppSettings.defaults();

        settings.setNotificationsActivees(true);
        settings.setNotificationsAutomatiquesActivees(true);
        settings.setFrequenceRappels(ReminderFrequency.QUOTIDIENNE);
        settings.setFuseauHoraire("Europe/Paris");

        when(settingsService.current()).thenReturn(settings);
    }

    @Test
    void shouldSendReminderForApprovedReservationOnce() {

        // ReminderService recherche les réservations à J+2
        LocalDate targetDate = LocalDate.now().plusDays(2);

        User user = new User();
        user.setId(12L);

        Space space = new Space();
        space.setNom("A101");

        Reservation reservation = Reservation.builder()
                .id(42L)
                .user(user)
                .space(space)
                .date(targetDate)
                .heureDebut(LocalTime.of(9, 0))
                .heureFin(LocalTime.of(10, 30))
                .statut(ReservationStatus.APPROVED)
                .actif(true)
                .build();

        when(reservationRepository.findByDate(targetDate))
                .thenReturn(List.of(reservation));

        when(reminderSentRepository.existsByReservationIdAndReminderKey(
                42L,
                "reservation-reminder-42-QUOTIDIENNE"
        )).thenReturn(false);

        int sent = reminderService.envoyerRappelsDuJour();

        assertEquals(1, sent);

        verify(notificationService).notifySystemUser(
                eq(user),
                eq(NotificationType.INFO),
                anyString(),
                anyString(),
                anyString()
        );

        verify(reminderSentRepository).save(any());
    }

    @Test
    void shouldSkipDuplicateReminderForSameReservation() {

        // ReminderService recherche les réservations à J+2
        LocalDate targetDate = LocalDate.now().plusDays(2);

        User user = new User();
        user.setId(12L);

        Space space = new Space();
        space.setNom("A101");

        Reservation reservation = Reservation.builder()
                .id(42L)
                .user(user)
                .space(space)
                .date(targetDate)
                .heureDebut(LocalTime.of(9, 0))
                .heureFin(LocalTime.of(10, 30))
                .statut(ReservationStatus.APPROVED)
                .actif(true)
                .build();

        when(reservationRepository.findByDate(targetDate))
                .thenReturn(List.of(reservation));

        when(reminderSentRepository.existsByReservationIdAndReminderKey(
                42L,
                "reservation-reminder-42-QUOTIDIENNE"
        )).thenReturn(true);

        int sent = reminderService.envoyerRappelsDuJour();

        assertEquals(0, sent);

        verify(notificationService, never()).notifySystemUser(
                any(),
                any(),
                anyString(),
                anyString(),
                anyString()
        );
    }
}