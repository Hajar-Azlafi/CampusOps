import axiosClient from './axiosClient'

export function fetchSpaces({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/spaces', { params }).then((res) => res.data)
}

export function searchSpaces(keyword) {
  return axiosClient.get('/spaces/search', { params: { keyword } }).then((res) => res.data)
}

export function getSpace(id) {
  return axiosClient.get(`/spaces/${id}`).then((res) => res.data)
}

export function getSpacesByFloor(floorId) {
  return axiosClient.get(`/spaces/floor/${floorId}`).then((res) => res.data)
}

export function getSpacesByBuilding(buildingId) {
  return axiosClient.get(`/spaces/building/${buildingId}`).then((res) => res.data)
}

export function getSpacesByType(type) {
  return axiosClient.get(`/spaces/type/${type}`).then((res) => res.data)
}

export function createSpace(payload) {
  return axiosClient.post('/spaces', payload).then((res) => res.data)
}

export function updateSpace(id, payload) {
  return axiosClient.put(`/spaces/${id}`, payload).then((res) => res.data)
}

export function deactivateSpace(id) {
  return axiosClient.patch(`/spaces/${id}/deactivate`)
}

export function activateSpace(id) {
  return axiosClient.patch(`/spaces/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : séances/réservations/examens
// qui bloquent la suppression de la salle. Alimente la modale de confirmation.
export function getSpaceDeletionImpact(id) {
  return axiosClient.get(`/spaces/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteSpace(id, cascade = false) {
  return axiosClient.delete(`/spaces/${id}`, { params: { cascade } })
}
