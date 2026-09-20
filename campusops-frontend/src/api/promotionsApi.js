import axiosClient from './axiosClient'

export function fetchPromotions({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/promotions', { params }).then((res) => res.data)
}

export function searchPromotions(keyword) {
  return axiosClient.get('/promotions/search', { params: { keyword } }).then((res) => res.data)
}

export function getPromotion(id) {
  return axiosClient.get(`/promotions/${id}`).then((res) => res.data)
}

export function getPromotionsByProgram(programId) {
  return axiosClient.get(`/promotions/program/${programId}`).then((res) => res.data)
}

export function getPromotionsByAcademicYear(academicYearId, { actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient
    .get(`/promotions/academic-year/${academicYearId}`, { params })
    .then((res) => res.data)
}

export function createPromotion(payload) {
  return axiosClient.post('/promotions', payload).then((res) => res.data)
}

export function updatePromotion(id, payload) {
  return axiosClient.put(`/promotions/${id}`, payload).then((res) => res.data)
}

export function deactivatePromotion(id) {
  return axiosClient.patch(`/promotions/${id}/deactivate`)
}

export function activatePromotion(id) {
  return axiosClient.patch(`/promotions/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : groupes supprimés en cascade
// et/ou éléments bloquants (séances/examens/emplois du temps rattachés).
export function getPromotionDeletionImpact(id) {
  return axiosClient.get(`/promotions/${id}/impact-suppression`).then((res) => res.data)
}

export function deletePromotion(id, cascade = false) {
  return axiosClient.delete(`/promotions/${id}`, { params: { cascade } })
}
