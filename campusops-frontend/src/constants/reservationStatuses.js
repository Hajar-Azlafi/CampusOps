export const RESERVATION_STATUSES = [
  { value: 'PENDING', label: 'En attente' },
  { value: 'APPROVED', label: 'Validée' },
  { value: 'REJECTED', label: 'Refusée' },
  { value: 'CANCELLED', label: 'Annulée' },
  { value: 'COMPLETED', label: 'Terminée' },
]

export function reservationStatusLabel(value) {
  return RESERVATION_STATUSES.find((s) => s.value === value)?.label ?? value
}
