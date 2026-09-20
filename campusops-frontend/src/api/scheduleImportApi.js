import axiosClient from './axiosClient'

export function downloadScheduleTemplate() {
  return axiosClient.get('/schedules/import/template', { responseType: 'blob' })
}

export function importSchedulesFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/schedules/import', formData, { headers: { 'Content-Type': undefined } })
    .then((res) => res.data)
}

export function fetchScheduleImportHistory() {
  return axiosClient.get('/schedules/import/history').then((res) => res.data)
}
