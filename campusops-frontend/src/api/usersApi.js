import axiosClient from './axiosClient'

export function fetchUsers({ role, department } = {}) {
  const params = {}
  if (role) params.role = role
  if (department) params.department = department
  return axiosClient.get('/users', { params }).then((res) => res.data)
}

export function searchUsers(keyword) {
  return axiosClient.get('/users/search', { params: { keyword } }).then((res) => res.data)
}

export function createUser(payload) {
  return axiosClient.post('/users', payload).then((res) => res.data)
}

export function updateUser(id, payload) {
  return axiosClient.put(`/users/${id}`, payload).then((res) => res.data)
}

export function changeUserRole(id, role) {
  return axiosClient.patch(`/users/${id}/role`, { role }).then((res) => res.data)
}

export function deactivateUser(id) {
  return axiosClient.patch(`/users/${id}/deactivate`)
}

export function reactivateUser(id) {
  return axiosClient.patch(`/users/${id}/reactivate`)
}

export function resetUserPassword(id) {
  return axiosClient.post(`/users/${id}/reset-password`).then((res) => res.data)
}
export function downloadImportTemplate() {
  return axiosClient.get('/users/import/template', { responseType: 'blob' })
}

export function importUsersFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/users/import', formData, { headers: { 'Content-Type': undefined } })
    .then((res) => res.data)
}