// Libellés d'affichage pour le type de formation d'un niveau/cycle.
// Centralisé afin de garder une terminologie cohérente dans toute l'application.

export const TYPE_FORMATION_LABELS = {
  INITIALE: 'Formation initiale',
  CONTINUE: 'Formation continue',
}

export function typeFormationLabel(value) {
  return TYPE_FORMATION_LABELS[value] ?? value ?? '—'
}

export const TYPE_FORMATION_OPTIONS = [
  { value: 'INITIALE', label: 'Formation initiale' },
  { value: 'CONTINUE', label: 'Formation continue' },
]
