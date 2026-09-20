import axiosClient from './axiosClient'

// Occupations supplémentaires génériques : soutenances (onglet « Planning
// soutenances ») et occupations ponctuelles diverses (onglet « Autre »).
// Les examens gardent leur propre API (`examensApi`) : contexte pédagogique
// complet. Toutes partagent en revanche le même moteur de disponibilité, donc une
// salle occupée par l'une d'elles n'est jamais proposée comme libre.
//
// Le backend borne automatiquement au périmètre du demandeur (AccessScopeService) :
// ADMIN → tout ; responsable pédagogique → uniquement ses filières.

/** Liste d'une catégorie (`SOUTENANCE` ou `AUTRE`), filtres facultatifs. */
export function fetchOccupations(filters = {}) {
  const params = { categorie: filters.categorie }
  if (filters.programId != null && filters.programId !== '') params.programId = filters.programId
  if (filters.date != null && filters.date !== '') params.date = filters.date
  return axiosClient.get('/occupations', { params }).then((res) => res.data)
}

export function getOccupation(id) {
  return axiosClient.get(`/occupations/${id}`).then((res) => res.data)
}

export function createOccupation(payload) {
  return axiosClient.post('/occupations', payload).then((res) => res.data)
}

export function updateOccupation(id, payload) {
  return axiosClient.put(`/occupations/${id}`, payload).then((res) => res.data)
}

export function deleteOccupation(id) {
  return axiosClient.delete(`/occupations/${id}`)
}
