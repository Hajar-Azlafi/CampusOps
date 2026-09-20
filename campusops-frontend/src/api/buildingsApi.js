import axiosClient from './axiosClient'

export function fetchBuildings({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/buildings', { params }).then((res) => res.data)
}

export function searchBuildings(keyword) {
  return axiosClient.get('/buildings/search', { params: { keyword } }).then((res) => res.data)
}

export function getBuilding(id) {
  return axiosClient.get(`/buildings/${id}`).then((res) => res.data)
}

export function createBuilding(payload) {
  return axiosClient.post('/buildings', payload).then((res) => res.data)
}

export function updateBuilding(id, payload) {
  return axiosClient.put(`/buildings/${id}`, payload).then((res) => res.data)
}

export function deactivateBuilding(id) {
  return axiosClient.patch(`/buildings/${id}/deactivate`)
}

export function activateBuilding(id) {
  return axiosClient.patch(`/buildings/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : étages/salles supprimés en
// cascade et/ou salles bloquantes (utilisées en séance/réservation/examen).
export function getBuildingDeletionImpact(id) {
  return axiosClient.get(`/buildings/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteBuilding(id, cascade = false) {
  return axiosClient.delete(`/buildings/${id}`, { params: { cascade } })
}
