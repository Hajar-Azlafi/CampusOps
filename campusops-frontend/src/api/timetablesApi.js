import axiosClient from './axiosClient'

// En-têtes d'emplois du temps (entité EmploiDuTemps) : un contexte unique =
// année + filière + niveau + promotion + groupe + semestre + session (§1). Le
// périmètre par filière est appliqué côté backend (AccessScopeService, §12) ;
// un responsable pédagogique ne reçoit que ses propres emplois du temps.

export function fetchTimetables(filters = {}) {
  const params = {}
  if (filters.academicYearId != null) params.academicYearId = filters.academicYearId
  if (filters.programId != null) params.programId = filters.programId
  if (filters.levelId != null) params.levelId = filters.levelId
  if (filters.groupId != null) params.groupId = filters.groupId
  if (filters.semesterId != null) params.semesterId = filters.semesterId
  if (filters.sessionId != null) params.sessionId = filters.sessionId
  if (filters.statut) params.statut = filters.statut
  return axiosClient.get('/timetables', { params }).then((res) => res.data)
}

export function getTimetable(id) {
  return axiosClient.get(`/timetables/${id}`).then((res) => res.data)
}

// Séances (Schedule) rattachées à un emploi du temps, pour la vue grille (§10).
export function getTimetableSeances(id) {
  return axiosClient.get(`/timetables/${id}/seances`).then((res) => res.data)
}

export function createTimetable(payload) {
  return axiosClient.post('/timetables', payload).then((res) => res.data)
}

export function publishTimetable(id) {
  return axiosClient.patch(`/timetables/${id}/publish`).then((res) => res.data)
}

export function archiveTimetable(id) {
  return axiosClient.patch(`/timetables/${id}/archive`).then((res) => res.data)
}

export function reopenTimetable(id) {
  return axiosClient.patch(`/timetables/${id}/reopen`).then((res) => res.data)
}

export function deleteTimetable(id) {
  return axiosClient.delete(`/timetables/${id}`)
}
