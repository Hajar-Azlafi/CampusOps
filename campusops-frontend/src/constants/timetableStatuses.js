// Statut d'un emploi du temps (entité EmploiDuTemps, cahier des charges §17).
// Cycle de vie non destructif (§20) : un emploi du temps archivé reste
// consultable et peut être rouvert.
export const TIMETABLE_STATUSES = [
  { value: 'BROUILLON', label: 'Brouillon' },
  { value: 'PUBLIE', label: 'Publié' },
  { value: 'ARCHIVE', label: 'Archivé' },
]

export function timetableStatusLabel(value) {
  return TIMETABLE_STATUSES.find((s) => s.value === value)?.label ?? value
}
