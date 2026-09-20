import axiosClient from './axiosClient'

export function fetchSchedules({ actif } = {}) {
  const params = {}
  if (actif !== undefined) params.actif = actif
  return axiosClient.get('/schedules', { params }).then((res) => res.data)
}

export function searchSchedules(keyword) {
  return axiosClient.get('/schedules/search', { params: { keyword } }).then((res) => res.data)
}

export function getSchedule(id) {
  return axiosClient.get(`/schedules/${id}`).then((res) => res.data)
}

export function getSchedulesByPromotion(promotionId) {
  return axiosClient.get(`/schedules/promotion/${promotionId}`).then((res) => res.data)
}

export function getSchedulesByGroup(groupId) {
  return axiosClient.get(`/schedules/group/${groupId}`).then((res) => res.data)
}

export function getSchedulesBySpace(spaceId) {
  return axiosClient.get(`/schedules/space/${spaceId}`).then((res) => res.data)
}

export function createSchedule(payload) {
  return axiosClient.post('/schedules', payload).then((res) => res.data)
}

export function updateSchedule(id, payload) {
  return axiosClient.put(`/schedules/${id}`, payload).then((res) => res.data)
}

export function deactivateSchedule(id) {
  return axiosClient.patch(`/schedules/${id}/deactivate`)
}

export function activateSchedule(id) {
  return axiosClient.patch(`/schedules/${id}/activate`)
}

// Statistiques de répartition des séances par type (§6/§19), calculées sur des
// données réelles. Le backend borne au périmètre (ADMIN → tout ; RP → ses
// filières) et affine selon le contexte fourni (tous les filtres sont optionnels :
// academicYearId, programId, levelId, promotionId, groupId, semesterId,
// emploiDuTempsId). Renvoie { total, repartition: [{ typeSeanceId, code, label,
// couleur, actif, value }] }.
export function fetchSessionTypeStats(params = {}) {
  const clean = {}
  Object.entries(params).forEach(([key, val]) => {
    if (val !== undefined && val !== '' && val !== null) clean[key] = val
  })
  return axiosClient.get('/schedules/stats/session-types', { params: clean }).then((res) => res.data)
}
