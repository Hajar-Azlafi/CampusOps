export const RESERVATION_TYPES = [
  { value: 'EXTRA_CLASS', label: 'Cours supplémentaire' },
  { value: 'MAKEUP_CLASS', label: 'Cours de rattrapage' },
  { value: 'EXAM', label: 'Examen' },
  { value: 'CLUB_MEETING', label: 'Réunion de club' },
  { value: 'EVENT', label: 'Événement' },
  { value: 'MAINTENANCE', label: 'Maintenance' },
  { value: 'OTHER', label: 'Autre' },
]

export function reservationTypeLabel(value) {
  return RESERVATION_TYPES.find((t) => t.value === value)?.label ?? value
}
