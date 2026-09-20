// Types de séance (cahier des charges §11). Extensible : « Autre » couvre les
// séances qui ne rentrent pas dans les catégories standard. À NE PAS confondre
// avec la « session universitaire » (normale / rattrapage…), voir academicSessionsApi.
export const SESSION_TYPES = [
  { value: 'COURS', label: 'Cours' },
  { value: 'TD', label: 'TD' },
  { value: 'TP', label: 'TP' },
  { value: 'EXAMEN', label: 'Examen' },
  { value: 'AUTRE', label: 'Autre' },
]

export function sessionTypeLabel(value) {
  return SESSION_TYPES.find((t) => t.value === value)?.label ?? value
}
