import axiosClient from './axiosClient'

// Types de séance configurables (Cours, TD, TP, Examen, Contrôle, Soutenance,
// Autre, + tout type ajouté par l'administrateur). Le backend les renvoie triés
// par ordre d'affichage croissant.
export function fetchSeanceTypes({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/seance-types', { params }).then((res) => res.data)
}

export function getSeanceType(id) {
  return axiosClient.get(`/seance-types/${id}`).then((res) => res.data)
}

export function createSeanceType(payload) {
  return axiosClient.post('/seance-types', payload).then((res) => res.data)
}

export function updateSeanceType(id, payload) {
  return axiosClient.put(`/seance-types/${id}`, payload).then((res) => res.data)
}

export function deactivateSeanceType(id) {
  return axiosClient.patch(`/seance-types/${id}/deactivate`)
}

export function activateSeanceType(id) {
  return axiosClient.patch(`/seance-types/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : séances qui utilisent ce type
// et bloquent sa suppression. Alimente la modale de confirmation.
export function getSeanceTypeDeletionImpact(id) {
  return axiosClient.get(`/seance-types/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteSeanceType(id, cascade = false) {
  return axiosClient.delete(`/seance-types/${id}`, { params: { cascade } })
}
