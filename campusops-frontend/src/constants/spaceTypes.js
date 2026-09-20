// Types proposés à la saisie (formulaire d'espace) et aux filtres. Liste
// volontairement restreinte aux types réellement utilisés sur le campus.
export const SPACE_TYPES = [
  { value: 'CLASSROOM', label: 'Salle de cours' },
  { value: 'COMPUTER_ROOM', label: 'Salle informatique' },
  { value: 'LABORATORY', label: 'Laboratoire' },
  { value: 'AMPHITHEATER', label: 'Amphithéâtre' },
  { value: 'MEETING_ROOM', label: 'Salle de réunion' },

]

// Libellés de TOUTES les valeurs de l'enum SpaceType du backend, y compris
// celles absentes de SPACE_TYPES. Des espaces historiques portent encore ces
// types (salle de conférence, salle polyvalente...) : sans cette table, le
// tableau de bord affichait la clé technique brute (« CONFERENCE_ROOM »).
export const SPACE_TYPE_LABELS = {
  CLASSROOM: 'Salle de cours',
  COMPUTER_ROOM: 'Salle informatique',
  LABORATORY: 'Laboratoire',
  AMPHITHEATER: 'Amphithéâtre',
  MEETING_ROOM: 'Salle de réunion',
  MULTIPURPOSE_ROOM: 'Salle polyvalente',
  CONFERENCE_ROOM: 'Salle de conférence',
  OTHER: 'Autre',
}

export function spaceTypeLabel(value) {
  if (!value) return value
  return SPACE_TYPE_LABELS[value] ?? value
}

// Spécialités de laboratoire (enum LabSpeciality côté backend). Ne concerne que
// les laboratoires : une salle informatique reste une simple salle informatique,
// sans spécialité. Nulle aussi pour les salles de cours, amphithéâtres, etc.
export const LAB_SPECIALITIES = [
  { value: 'INFORMATIQUE', label: 'Informatique' },
  { value: 'RESEAUX', label: 'Réseaux' },
  { value: 'PHYSIQUE', label: 'Physique' },
  { value: 'ELECTRICITE', label: 'Électricité' },
  { value: 'ELECTRONIQUE', label: 'Électronique' },
  { value: 'CHIMIE', label: 'Chimie' },
  { value: 'BIOLOGIE', label: 'Biologie' },
  { value: 'MECANIQUE', label: 'Mécanique' },
]

// Types d'espaces pouvant porter une spécialité : uniquement les laboratoires.
export const SPECIALIZABLE_SPACE_TYPES = ['LABORATORY']

export function labSpecialityLabel(value) {
  if (!value) return null
  return LAB_SPECIALITIES.find((s) => s.value === value)?.label ?? value
}
