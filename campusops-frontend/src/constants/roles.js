export const ROLES = [
  { value: 'ADMIN', label: 'Administrateur' },
  { value: 'RESPONSABLE_PEDAGOGIQUE', label: 'Responsable pedagogique' },
  { value: 'ENSEIGNANT', label: 'Enseignant' },
  { value: 'RESPONSABLE_CLUB', label: 'Responsable de club' },
]

export function roleLabel(value) {
  return ROLES.find((r) => r.value === value)?.label ?? value
}