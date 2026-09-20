import { isValidHexColor } from './brandColors'

// Validations en direct de la page Paramètres (§18).
//
// Chaque fonction renvoie `null` si la valeur est acceptable, sinon un message
// en français affiché tel quel sous le champ. Les règles reproduisent
// EXACTEMENT celles du backend (Bean Validation des DTO + contrôles croisés de
// SettingsService) : le bouton « Enregistrer » ne doit jamais laisser partir une
// valeur que le serveur refusera ensuite.

const TELEPHONE_REGEX = /^[+0-9][0-9 .\-()]{5,24}$/
const SITE_WEB_REGEX = /^https?:\/\/\S{3,}$/
const HEURE_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/

/** Champ texte obligatoire, avec longueur maximale du backend. */
export function texteObligatoire(value, max) {
  const texte = (value ?? '').trim()
  if (!texte) return 'Ce champ est obligatoire.'
  if (max && texte.length > max) return `Maximum ${max} caractères.`
  return null
}

/** Champ texte facultatif : seule la longueur est contrôlée. */
export function texteFacultatif(value, max) {
  const texte = (value ?? '').trim()
  if (texte && max && texte.length > max) return `Maximum ${max} caractères.`
  return null
}

export function emailFacultatif(value) {
  const texte = (value ?? '').trim()
  if (!texte) return null
  if (texte.length > 150) return 'Maximum 150 caractères.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(texte)) {
    return 'Adresse e-mail invalide. Exemple : contact@universite.ma'
  }
  return null
}

export function siteWebFacultatif(value) {
  const texte = (value ?? '').trim()
  if (!texte) return null
  if (!SITE_WEB_REGEX.test(texte)) {
    return 'Adresse invalide : elle doit commencer par http:// ou https://'
  }
  return null
}

export function telephoneFacultatif(value) {
  const texte = (value ?? '').trim()
  if (!texte) return null
  if (!TELEPHONE_REGEX.test(texte)) {
    return 'Numéro invalide (6 à 25 caractères). Exemple : +212 522 00 00 00'
  }
  return null
}

export function couleurObligatoire(value) {
  const texte = (value ?? '').trim()
  if (!texte) return 'La couleur est obligatoire.'
  if (!isValidHexColor(texte)) return 'Couleur invalide. Format attendu : #1A2B3C'
  return null
}

/**
 * Entier borné. `min`/`max` sont ceux du backend ; un champ vidé est signalé
 * comme obligatoire plutôt que transformé silencieusement en zéro.
 */
export function entierBorne(value, { min, max, unite } = {}) {
  const texte = String(value ?? '').trim()
  if (!texte) return 'Ce champ est obligatoire.'
  if (!/^-?\d+$/.test(texte)) return 'Nombre entier attendu.'
  const nombre = Number(texte)
  const suffixe = unite ? ` ${unite}` : ''
  if (min !== undefined && nombre < min) return `Minimum ${min}${suffixe}.`
  if (max !== undefined && nombre > max) return `Maximum ${max}${suffixe}.`
  return null
}

export function heureObligatoire(value) {
  const texte = (value ?? '').trim()
  if (!texte) return "L'heure est obligatoire."
  if (!HEURE_REGEX.test(texte)) return 'Heure attendue au format HH:mm.'
  return null
}

/** Minutes depuis minuit, ou `null` si l'heure n'est pas exploitable. */
export function minutesDepuisMinuit(value) {
  const texte = (value ?? '').trim()
  if (!HEURE_REGEX.test(texte)) return null
  const [heures, minutes] = texte.split(':').map(Number)
  return heures * 60 + minutes
}

/** Durée en minutes rendue lisible : « 240 minutes (4 h) ». */
export function dureeLisible(minutes) {
  const total = Number(minutes)
  if (!Number.isFinite(total) || total <= 0) return null
  const heures = Math.floor(total / 60)
  const reste = total % 60
  if (heures === 0) return `${reste} min`
  if (reste === 0) return `${heures} h`
  return `${heures} h ${String(reste).padStart(2, '0')}`
}
