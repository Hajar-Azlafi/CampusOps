// Type de présence d'une séance (cahier des charges §6).
// - Présentiel : une salle est OBLIGATOIRE.
// - Distanciel : la salle est facultative (séance à distance).
// La règle est appliquée côté backend ; côté frontend elle guide la saisie.
export const PRESENCE_TYPES = [
  { value: 'PRESENTIEL', label: 'Présentiel' },
  { value: 'DISTANCIEL', label: 'Distanciel' },
]

export function presenceTypeLabel(value) {
  return PRESENCE_TYPES.find((t) => t.value === value)?.label ?? value
}
