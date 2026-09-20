export const NOTIFICATION_TYPES = [
  { value: 'RESERVATION_APPROVED', label: 'Réservation validée' },
  { value: 'RESERVATION_REJECTED', label: 'Réservation refusée' },
  { value: 'RESERVATION_CANCELLED', label: 'Réservation annulée' },
  { value: 'NEW_RESERVATION', label: 'Nouvelle réservation' },
  { value: 'SCHEDULE_IMPORTED', label: 'Emploi du temps importé' },
  { value: 'SYSTEM', label: 'Système' },
  { value: 'WARNING', label: 'Avertissement' },
  { value: 'INFO', label: 'Information' },
]

export function notificationTypeLabel(value) {
  return NOTIFICATION_TYPES.find((t) => t.value === value)?.label ?? value
}
