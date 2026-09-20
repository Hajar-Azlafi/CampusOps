import axiosClient from './axiosClient'

export function fetchAuditLogs(academicYearId) {
  const params = {}
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/audit', { params }).then((res) => res.data)
}

export function searchAuditLogs({ userId, action, module, start, end } = {}) {
  const params = {}
  if (userId) params.userId = userId
  if (action) params.action = action
  if (module) params.module = module
  if (start) params.start = start
  if (end) params.end = end
  return axiosClient.get('/audit/search', { params }).then((res) => res.data)
}

export function fetchMyHistory(academicYearId) {
  const params = {}
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/history/me', { params }).then((res) => res.data)
}

export function fetchHistory({ userId, academicYearId } = {}) {
  const params = {}
  if (userId) params.userId = userId
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/history', { params }).then((res) => res.data)
}
