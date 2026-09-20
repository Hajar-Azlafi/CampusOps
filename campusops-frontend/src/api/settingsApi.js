// Configuration globale de l'université (Module 11). Une seule configuration
// existe côté serveur : toutes les fonctions ci-dessous agissent donc sur cet
// unique enregistrement, sans identifiant.
//
// Deux familles d'appels, volontairement séparées :
//   - `/settings...`  : réservé à l'ADMIN (le backend le vérifie, cf. §14) ;
//   - `/settings/branding` + `/logo` + `/favicon` en GET : public, car la page
//     de connexion doit afficher l'identité visuelle AVANT tout jeton.

import axiosClient from './axiosClient'

/**
 * Origine du serveur (sans le `/api`), nécessaire pour les balises `<img>` :
 * une image n'est pas chargée par axios, donc un chemin relatif viserait le
 * serveur de développement Vite et non le backend.
 */
const SERVER_ORIGIN = String(axiosClient.defaults.baseURL || '').replace(/\/api\/?$/, '')

/**
 * Transforme un chemin renvoyé par le backend (`/api/settings/logo?v=42`) en URL
 * absolue affichable. Renvoie `null` si aucun média n'est enregistré.
 */
export function mediaSrc(path) {
  if (!path) return null
  if (/^https?:\/\//i.test(path)) return path
  return `${SERVER_ORIGIN}${path}`
}

// ----------------------------- Lecture complète -----------------------------

/** Toutes les sections + médias + contexte académique (page Paramètres). */
export function fetchSettings() {
  return axiosClient.get('/settings').then((res) => res.data)
}

/** Mise à jour partielle : seules les sections envoyées sont appliquées. */
export function updateSettings(payload) {
  return axiosClient.put('/settings', payload).then((res) => res.data)
}

// ----------------------------- Sections ciblées -----------------------------

export function updateUniversity(payload) {
  return axiosClient.put('/settings/university', payload).then((res) => res.data)
}

export function updateReservationSettings(payload) {
  return axiosClient.put('/settings/reservations', payload).then((res) => res.data)
}

export function updateWorkingHours(payload) {
  return axiosClient.put('/settings/working-hours', payload).then((res) => res.data)
}

export function updateSecuritySettings(payload) {
  return axiosClient.put('/settings/security', payload).then((res) => res.data)
}

export function updateNotificationSettings(payload) {
  return axiosClient.put('/settings/notifications', payload).then((res) => res.data)
}

export function updateImportSettings(payload) {
  return axiosClient.put('/settings/import', payload).then((res) => res.data)
}

export function updateDisplaySettings(payload) {
  return axiosClient.put('/settings/display', payload).then((res) => res.data)
}

// -------------------------- Identité visuelle (§11) --------------------------

/** Nom, couleurs, thème et URL des médias — accessible sans authentification. */
export function fetchBranding() {
  return axiosClient.get('/settings/branding').then((res) => res.data)
}

/**
 * Envoi d'un logo ou d'un favicon. `Content-Type: undefined` laisse le
 * navigateur poser la frontière multipart (le client axios impose du JSON par
 * défaut) — même procédé que les imports Excel.
 */
export function uploadBrandingMedia(type, file) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post(`/settings/${type}`, formData, { headers: { 'Content-Type': undefined } })
    .then((res) => res.data)
}

export function deleteBrandingMedia(type) {
  return axiosClient.delete(`/settings/${type}`)
}
