import axiosClient from './axiosClient'

export function fetchReservations({ statut, academicYearId } = {}) {
  const params = {}
  if (statut) params.statut = statut
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/reservations', { params }).then((res) => res.data)
}

export function getMyReservations() {
  return axiosClient.get('/reservations/me').then((res) => res.data)
}

export function getReservation(id) {
  return axiosClient.get(`/reservations/${id}`).then((res) => res.data)
}

export function getReservationsByUser(userId) {
  return axiosClient.get(`/reservations/user/${userId}`).then((res) => res.data)
}

export function getReservationsBySpace(spaceId) {
  return axiosClient.get(`/reservations/space/${spaceId}`).then((res) => res.data)
}

export function getReservationsByDate(date) {
  return axiosClient.get('/reservations/date', { params: { date } }).then((res) => res.data)
}

export function getReservationsByPeriod(start, end) {
  return axiosClient
    .get('/reservations/period', { params: { start, end } })
    .then((res) => res.data)
}

export function searchReservations(keyword, academicYearId) {
  const params = { keyword }
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/reservations/search', { params }).then((res) => res.data)
}

export function createReservation(payload) {
  return axiosClient.post('/reservations', payload).then((res) => res.data)
}

export function updateReservation(id, payload) {
  return axiosClient.put(`/reservations/${id}`, payload).then((res) => res.data)
}

export function approveReservation(id) {
  return axiosClient.patch(`/reservations/${id}/approve`).then((res) => res.data)
}

export function rejectReservation(id) {
  return axiosClient.patch(`/reservations/${id}/reject`).then((res) => res.data)
}

export function cancelReservation(id) {
  return axiosClient.patch(`/reservations/${id}/cancel`).then((res) => res.data)
}
