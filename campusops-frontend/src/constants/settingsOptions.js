// Métadonnées de la page Paramètres (Module 11) : onglets et libellés des
// énumérations du backend. Regroupées ici pour que la page reste de la mise en
// page, et non un dictionnaire.

/**
 * Onglets, dans l'ordre imposé par le cahier des charges (§15).
 *
 * « Configuration initiale » (§15-§19) est placé en premier afin de guider la
 * mise en route avant les autres réglages.
 */
export const SETTINGS_TABS = [
  { slug: 'demarrage', label: 'Configuration initiale' },
  { slug: 'universite', label: 'Université' },
  { slug: 'reservations', label: 'Réservations' },
  { slug: 'horaires', label: 'Horaires' },
  { slug: 'securite', label: 'Sécurité' },
  { slug: 'notifications', label: 'Notifications' },
  { slug: 'imports', label: 'Imports' },
  { slug: 'affichage', label: 'Affichage' },
]

export const DEFAULT_SETTINGS_TAB = SETTINGS_TABS[0]

/** Onglet correspondant à un slug d'URL, avec repli sur le premier. */
export function settingsTabBySlug(slug) {
  return SETTINGS_TABS.find((tab) => tab.slug === slug) ?? DEFAULT_SETTINGS_TAB
}

// --------------------------- Énumérations backend ---------------------------
//
// Chaque option porte son libellé français ; la `value` reste l'énumération
// brute attendue par le backend.

/** `AppTheme` : thème appliqué aux utilisateurs qui n'ont pas encore choisi. */
export const THEME_OPTIONS = [
  { value: 'CLAIR', label: 'Clair' },
  { value: 'SOMBRE', label: 'Sombre' },
  { value: 'SYSTEME', label: 'Selon le système' },
]

/** `DateDisplayFormat`, avec un exemple lisible. */
export const DATE_FORMAT_OPTIONS = [
  { value: 'JJ_MM_AAAA', label: 'JJ/MM/AAAA — 31/12/2026' },
  { value: 'AAAA_MM_JJ', label: 'AAAA-MM-JJ — 2026-12-31' },
  { value: 'MM_JJ_AAAA', label: 'MM/JJ/AAAA — 12/31/2026' },
]

/** `TimeDisplayFormat`. */
export const TIME_FORMAT_OPTIONS = [
  { value: 'H24', label: '24 heures — 14:30' },
  { value: 'H12', label: '12 heures — 02:30 PM' },
]

/** `ReminderFrequency` : avance du rappel envoyé avant une réservation. */
export const REMINDER_OPTIONS = [
  { value: 'JAMAIS', label: 'Jamais' },
  { value: 'QUOTIDIENNE', label: 'La veille de la réservation' },
  { value: 'HEBDOMADAIRE', label: 'Une semaine avant' },
]

/** Fuseaux proposés ; le champ reste libre car le backend accepte tout ZoneId. */
export const TIMEZONE_SUGGESTIONS = [
  'Africa/Casablanca',
  'Africa/Algiers',
  'Africa/Tunis',
  'Africa/Cairo',
  'Europe/Paris',
  'Europe/Madrid',
  'Europe/London',
  'UTC',
]
