import axiosClient from './axiosClient'

export function fetchFloors({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/floors', { params }).then((res) => res.data)
}

export function searchFloors(keyword) {
  return axiosClient.get('/floors/search', { params: { keyword } }).then((res) => res.data)
}

export function getFloor(id) {
  return axiosClient.get(`/floors/${id}`).then((res) => res.data)
}

export function getFloorsByBuilding(buildingId) {
  return axiosClient.get(`/floors/building/${buildingId}`).then((res) => res.data)
}

export function createFloor(payload) {
  return axiosClient.post('/floors', payload).then((res) => res.data)
}

export function updateFloor(id, payload) {
  return axiosClient.put(`/floors/${id}`, payload).then((res) => res.data)
}

export function deactivateFloor(id) {
  return axiosClient.patch(`/floors/${id}/deactivate`)
}

export function activateFloor(id) {
  return axiosClient.patch(`/floors/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : salles supprimées en cascade
// et/ou salles bloquantes (utilisées en séance/réservation/examen).
export function getFloorDeletionImpact(id) {
  return axiosClient.get(`/floors/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteFloor(id, cascade = false) {
  return axiosClient.delete(`/floors/${id}`, { params: { cascade } })
}
