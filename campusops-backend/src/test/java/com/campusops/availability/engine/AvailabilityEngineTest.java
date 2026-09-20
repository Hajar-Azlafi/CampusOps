package com.campusops.availability.engine;

import com.campusops.enums.OccupancySource;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityEngineTest {

    private final AvailabilityEngine engine = new AvailabilityEngine(
            null, null, null, null);

    @Test
    void keepsFreePeriodsBeforeAndAfterAcceptedReservation() {
        List<FreeInterval> free = engine.freePeriods(
                LocalTime.of(8, 0), LocalTime.of(18, 0),
                List.of(interval(10, 0, 12, 0)), 0);

        assertThat(free).extracting(FreeInterval::debut, FreeInterval::fin)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(18, 0)));
    }

    @Test
    void subtractsSeveralSeparatedReservations() {
        List<FreeInterval> free = engine.freePeriods(
                LocalTime.of(8, 0), LocalTime.of(18, 0),
                List.of(interval(10, 0, 12, 0), interval(14, 0, 15, 30)), 0);

        assertThat(free).extracting(FreeInterval::debut, FreeInterval::fin)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(8, 0), LocalTime.of(10, 0)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(12, 0), LocalTime.of(14, 0)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(15, 30), LocalTime.of(18, 0)));
    }

    @Test
    void treatsTouchingLimitsAsAvailable() {
        OccupancyInterval occupied = interval(10, 0, 12, 0);

        assertThat(occupied.overlaps(LocalTime.of(8, 0), LocalTime.of(10, 0))).isFalse();
        assertThat(occupied.overlaps(LocalTime.of(12, 0), LocalTime.of(14, 0))).isFalse();
        assertThat(occupied.overlaps(LocalTime.of(9, 0), LocalTime.of(10, 30))).isTrue();
        assertThat(occupied.overlaps(LocalTime.of(11, 0), LocalTime.of(13, 0))).isTrue();
        assertThat(occupied.overlaps(LocalTime.of(9, 0), LocalTime.of(12, 0))).isTrue();
    }

    @Test
    void doesNotCreateFreePeriodForAnExactReservationWhenMinimumIsPositive() {
        List<FreeInterval> free = engine.freePeriods(
                LocalTime.of(8, 0), LocalTime.of(18, 0),
                List.of(interval(8, 0, 18, 0)), 0);

        assertThat(free).isEmpty();
    }

    private static OccupancyInterval interval(int startHour, int startMinute,
                                               int endHour, int endMinute) {
        return new OccupancyInterval(LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute),
                OccupancySource.RESERVATION_ACCEPTEE,
                "Réservation acceptée");
    }
}