import axiosClient from './axiosClient'

export function fetchGroups({ actif, academicYearId } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/groups', { params }).then((res) => res.data)
}

export function searchGroups(keyword) {
  return axiosClient.get('/groups/search', { params: { keyword } }).then((res) => res.data)
}

export function getGroup(id) {
  return axiosClient.get(`/groups/${id}`).then((res) => res.data)
}

export function getGroupsByPromotion(promotionId) {
  return axiosClient.get(`/groups/promotion/${promotionId}`).then((res) => res.data)
}

export function getGroupsByAcademicYear(academicYearId, { actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient
    .get(`/groups/academic-year/${academicYearId}`, { params })
    .then((res) => res.data)
}

export function createGroup(payload) {
  return axiosClient.post('/groups', payload).then((res) => res.data)
}

export function updateGroup(id, payload) {
  return axiosClient.put(`/groups/${id}`, payload).then((res) => res.data)
}

export function deactivateGroup(id) {
  return axiosClient.patch(`/groups/${id}/deactivate`)
}

export function activateGroup(id) {
  return axiosClient.patch(`/groups/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : séances/examens/emplois du temps
// rattachés au groupe qui bloquent sa suppression. Alimente la modale.
export function getGroupDeletionImpact(id) {
  return axiosClient.get(`/groups/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteGroup(id, cascade = false) {
  return axiosClient.delete(`/groups/${id}`, { params: { cascade } })
}
