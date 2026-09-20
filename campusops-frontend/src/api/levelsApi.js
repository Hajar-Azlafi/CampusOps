import axiosClient from './axiosClient'

export function fetchLevels({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/levels', { params }).then((res) => res.data)
}

export function searchLevels(keyword) {
  return axiosClient.get('/levels/search', { params: { keyword } }).then((res) => res.data)
}

export function getLevel(id) {
  return axiosClient.get(`/levels/${id}`).then((res) => res.data)
}

export function createLevel(payload) {
  return axiosClient.post('/levels', payload).then((res) => res.data)
}

export function updateLevel(id, payload) {
  return axiosClient.put(`/levels/${id}`, payload).then((res) => res.data)
}

// Pas d'activate/deactivate cote niveau : le niveau / cycle est un referentiel
// taxonomique stable dont le statut actif/inactif n'a aucun sens metier (il
// n'entre dans aucun predicat d'usabilite). Le backend n'expose donc pas ces
// endpoints (§9/§26) ; un niveau obsolete se supprime.
// Aperçu d'impact avant suppression (§ preview) : semestres/filières bloquants.
export function getLevelDeletionImpact(id) {
  return axiosClient.get(`/levels/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteLevel(id, cascade = false) {
  return axiosClient.delete(`/levels/${id}`, { params: { cascade } })
}
