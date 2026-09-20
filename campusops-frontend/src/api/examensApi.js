import axiosClient from './axiosClient'

// Examens datés (Lot 2 F2) : évènements ponctuels (date + créneau + salle +
// module + audience), distincts des séances hebdomadaires de l'emploi du temps.
// Le backend borne automatiquement au périmètre du demandeur : ADMIN → tous les
// examens ; responsable pédagogique → uniquement ceux de ses filières.
export function fetchExamens(filters = {}) {
  const params = {}
  if (filters.academicYearId != null && filters.academicYearId !== '') params.academicYearId = filters.academicYearId
  if (filters.programId != null && filters.programId !== '') params.programId = filters.programId
  if (filters.promotionId != null && filters.promotionId !== '') params.promotionId = filters.promotionId
  if (filters.groupId != null && filters.groupId !== '') params.groupId = filters.groupId
  if (filters.semesterId != null && filters.semesterId !== '') params.semesterId = filters.semesterId
  if (filters.sessionId != null && filters.sessionId !== '') params.sessionId = filters.sessionId
  if (filters.moduleId != null && filters.moduleId !== '') params.moduleId = filters.moduleId
  if (filters.date != null && filters.date !== '') params.date = filters.date
  return axiosClient.get('/examens', { params }).then((res) => res.data)
}

export function getExamen(id) {
  return axiosClient.get(`/examens/${id}`).then((res) => res.data)
}

export function createExamen(payload) {
  return axiosClient.post('/examens', payload).then((res) => res.data)
}

export function updateExamen(id, payload) {
  return axiosClient.put(`/examens/${id}`, payload).then((res) => res.data)
}

export function deleteExamen(id) {
  return axiosClient.delete(`/examens/${id}`)
}
