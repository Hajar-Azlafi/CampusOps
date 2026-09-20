import axiosClient from './axiosClient'

export function fetchAcademicYears({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/academic-years', { params }).then((res) => res.data)
}

export function searchAcademicYears(keyword) {
  return axiosClient.get('/academic-years/search', { params: { keyword } }).then((res) => res.data)
}

export function getAcademicYear(id) {
  return axiosClient.get(`/academic-years/${id}`).then((res) => res.data)
}

export function getCurrentAcademicYear() {
  return axiosClient.get('/academic-years/current').then((res) => res.data)
}

export function createAcademicYear(payload) {
  return axiosClient.post('/academic-years', payload).then((res) => res.data)
}

export function updateAcademicYear(id, payload) {
  return axiosClient.put(`/academic-years/${id}`, payload).then((res) => res.data)
}

export function deactivateAcademicYear(id) {
  return axiosClient.patch(`/academic-years/${id}/deactivate`)
}

export function activateAcademicYear(id) {
  return axiosClient.patch(`/academic-years/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : promotions/séances/emplois du
// temps/occupations/réservations rattachés à l'année, qui la bloquent afin de ne
// jamais détruire d'historique académique par accident. Préférer la désactivation.
export function getAcademicYearDeletionImpact(id) {
  return axiosClient.get(`/academic-years/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteAcademicYear(id, cascade = false) {
  return axiosClient.delete(`/academic-years/${id}`, { params: { cascade } })
}

/**
 * Passage manuel à l'année universitaire suivante : crée/active l'année
 * suivante et amorce sa structure. Aucune donnée n'est supprimée.
 */
export function rolloverAcademicYear() {
  return axiosClient.post('/academic-years/rollover').then((res) => res.data)
}
