package com.campusops.enums;

/**
 * Actions importantes tracees dans le journal d'audit. Chaque operation
 * sensible de l'application est enregistree avec l'une de ces valeurs.
 */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    CREATE,
    UPDATE,
    SOFT_DELETE,
    DELETE,
    EXCEL_IMPORT,
    RESERVATION,
    APPROVAL,
    REJECTION,
    CANCELLATION,
    ACTIVATION,
    DEACTIVATION,
    SYSTEM
}
