import axiosClient from './axiosClient'

export function fetchEquipments({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/equipments', { params }).then((res) => res.data)
}

export function searchEquipments(keyword) {
  return axiosClient.get('/equipments/search', { params: { keyword } }).then((res) => res.data)
}

export function getEquipment(id) {
  return axiosClient.get(`/equipments/${id}`).then((res) => res.data)
}

export function createEquipment(payload) {
  return axiosClient.post('/equipments', payload).then((res) => res.data)
}

export function updateEquipment(id, payload) {
  return axiosClient.put(`/equipments/${id}`, payload).then((res) => res.data)
}

export function deactivateEquipment(id) {
  return axiosClient.patch(`/equipments/${id}/deactivate`)
}

export function activateEquipment(id) {
  return axiosClient.patch(`/equipments/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : l'équipement sera détaché des
// salles auxquelles il est associé (cascade technique, jamais bloquant).
export function getEquipmentDeletionImpact(id) {
  return axiosClient.get(`/equipments/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteEquipment(id, cascade = false) {
  return axiosClient.delete(`/equipments/${id}`, { params: { cascade } })
}

// Association espace <-> equipements
export function getSpaceEquipments(spaceId) {
  return axiosClient.get(`/spaces/${spaceId}/equipments`).then((res) => res.data)
}

export function assignSpaceEquipments(spaceId, equipmentIds) {
  return axiosClient
    .post(`/spaces/${spaceId}/equipments`, { equipmentIds })
    .then((res) => res.data)
}

export function removeSpaceEquipment(spaceId, equipmentId) {
  return axiosClient.delete(`/spaces/${spaceId}/equipments/${equipmentId}`)
}
