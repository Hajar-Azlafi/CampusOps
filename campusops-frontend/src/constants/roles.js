export const ROLES = [
  { value: 'ADMIN', label: 'Administrateur' },
  { value: 'RESPONSABLE_PEDAGOGIQUE', label: 'Responsable pédagogique' },
  { value: 'ENSEIGNANT', label: 'Enseignant' },
  { value: 'RESPONSABLE_CLUB', label: 'Responsable de club' },
]

export function roleLabel(value) {
  return ROLES.find((r) => r.value === value)?.label ?? value
}

// Roles qui utilisent l'interface simplifiee (navbar + recherche/reservation),
// sans la sidebar d'administration.
export const SIMPLE_NAV_ROLES = ['ENSEIGNANT', 'RESPONSABLE_CLUB']

// Page d'accueil apres connexion, selon le role.
export function homeRouteForRole(role) {
  if (role === 'ADMIN') return '/dashboard'
  if (role === 'RESPONSABLE_PEDAGOGIQUE') return '/rp'
  return '/availability'
}