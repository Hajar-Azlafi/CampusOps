export const RESERVATION_PRIORITIES = [
  { value: 'HIGH', label: 'Haute' },
  { value: 'MEDIUM', label: 'Moyenne' },
  { value: 'LOW', label: 'Basse' },
]

export function reservationPriorityLabel(value) {
  return RESERVATION_PRIORITIES.find((p) => p.value === value)?.label ?? value
}
