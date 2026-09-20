import axiosClient from './axiosClient'

export function fetchDepartments({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/departments', { params }).then((res) => res.data)
}

export function searchDepartments(keyword) {
  return axiosClient.get('/departments/search', { params: { keyword } }).then((res) => res.data)
}

export function getDepartment(id) {
  return axiosClient.get(`/departments/${id}`).then((res) => res.data)
}

export function createDepartment(payload) {
  return axiosClient.post('/departments', payload).then((res) => res.data)
}

export function updateDepartment(id, payload) {
  return axiosClient.put(`/departments/${id}`, payload).then((res) => res.data)
}

export function deactivateDepartment(id) {
  return axiosClient.patch(`/departments/${id}/deactivate`)
}

export function activateDepartment(id) {
  return axiosClient.patch(`/departments/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : enfants supprimés en cascade
// (filières, promotions, groupes, modules) et/ou éléments bloquants. Alimente la
// modale de confirmation.
export function getDepartmentDeletionImpact(id) {
  return axiosClient.get(`/departments/${id}/impact-suppression`).then((res) => res.data)
}

// `cascade` doit être confirmé côté frontend (modale) pour autoriser la
// suppression des enfants structurels.
export function deleteDepartment(id, cascade = false) {
  return axiosClient.delete(`/departments/${id}`, { params: { cascade } })
}
