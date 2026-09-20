// Types de jours non ouvrables (§4). Le libellé backend (`typeLibelle`) fait foi
// à l'affichage ; cette liste sert au formulaire (sélecteur) et à un rendu de
// repli. Les valeurs doivent correspondre à l'enum NonWorkingDayType du backend.
export const NON_WORKING_DAY_TYPES = [
  { value: 'FERIE_NATIONAL', label: 'Férié national' },
  { value: 'FERIE_RELIGIEUX', label: 'Fête religieuse' },
  { value: 'VACANCES', label: 'Vacances' },
  { value: 'FERMETURE_EXCEPTIONNELLE', label: 'Fermeture exceptionnelle' },
  { value: 'PERSONNALISE', label: 'Personnalisé' },
]

const BY_VALUE = Object.fromEntries(NON_WORKING_DAY_TYPES.map((t) => [t.value, t.label]))

export function nonWorkingDayTypeLabel(type) {
  return BY_VALUE[type] || type || '—'
}
