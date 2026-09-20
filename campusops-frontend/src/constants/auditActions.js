export const AUDIT_ACTIONS = [
  { value: 'LOGIN', label: 'Connexion' },
  { value: 'LOGOUT', label: 'Déconnexion' },
  { value: 'CREATE', label: 'Création' },
  { value: 'UPDATE', label: 'Modification' },
  { value: 'SOFT_DELETE', label: 'Suppression logique' },
  { value: 'DELETE', label: 'Suppression' },
  { value: 'EXCEL_IMPORT', label: 'Import Excel' },
  { value: 'RESERVATION', label: 'Réservation' },
  { value: 'APPROVAL', label: 'Validation' },
  { value: 'REJECTION', label: 'Refus' },
  { value: 'CANCELLATION', label: 'Annulation' },
  { value: 'ACTIVATION', label: 'Activation' },
  { value: 'DEACTIVATION', label: 'Désactivation' },
  { value: 'SYSTEM', label: 'Erreur système' },
]

export function auditActionLabel(value) {
  return AUDIT_ACTIONS.find((a) => a.value === value)?.label ?? value
}
