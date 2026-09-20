package com.campusops.enums;

/**
 * Types de notifications internes affichees aux utilisateurs. Le type permet
 * de categoriser et d'illustrer chaque notification cote interface.
 */
public enum NotificationType {
    RESERVATION_APPROVED,
    RESERVATION_REJECTED,
    RESERVATION_CANCELLED,
    NEW_RESERVATION,
    SCHEDULE_IMPORTED,
    SYSTEM,
    WARNING,
    INFO
}
