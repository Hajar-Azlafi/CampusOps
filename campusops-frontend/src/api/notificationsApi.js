import axiosClient from './axiosClient'

export function fetchNotifications(academicYearId) {
  const params = {}
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/notifications', { params }).then((res) => res.data)
}

export function fetchUnreadNotifications(academicYearId) {
  const params = {}
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient.get('/notifications/unread', { params }).then((res) => res.data)
}

export function countUnreadNotifications() {
  return axiosClient.get('/notifications/unread/count').then((res) => res.data)
}

export function markNotificationAsRead(id) {
  return axiosClient.patch(`/notifications/${id}/read`).then((res) => res.data)
}

export function markAllNotificationsAsRead() {
  return axiosClient.patch('/notifications/read-all').then((res) => res.data)
}
