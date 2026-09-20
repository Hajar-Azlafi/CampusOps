import axiosClient from './axiosClient'

export function fetchSemesters({ actif, levelId } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  if (levelId !== undefined && levelId !== '' && levelId !== null) params.levelId = levelId
  return axiosClient.get('/semesters', { params }).then((res) => res.data)
}

export function searchSemesters(keyword) {
  return axiosClient.get('/semesters/search', { params: { keyword } }).then((res) => res.data)
}

export function getSemester(id) {
  return axiosClient.get(`/semesters/${id}`).then((res) => res.data)
}

export function createSemester(payload) {
  return axiosClient.post('/semesters', payload).then((res) => res.data)
}

export function updateSemester(id, payload) {
  return axiosClient.put(`/semesters/${id}`, payload).then((res) => res.data)
}

export function deactivateSemester(id) {
  return axiosClient.patch(`/semesters/${id}/deactivate`)
}

export function activateSemester(id) {
  return axiosClient.patch(`/semesters/${id}/activate`)
}

/** Semestres actuellement courants (un niveau peut en avoir plusieurs). */
export function fetchCurrentSemesters() {
  return axiosClient.get('/semesters/current').then((res) => res.data)
}

/**
 * Ajoute le semestre à l'ensemble des courants de son niveau. Un niveau peut en
 * compter plusieurs simultanément (cohortes coexistantes) : les autres ne sont
 * pas retirés.
 */
export function setSemesterCourant(id) {
  return axiosClient.patch(`/semesters/${id}/courant`).then((res) => res.data)
}

/** Retire le semestre de l'ensemble des courants de son niveau. */
export function unsetSemesterCourant(id) {
  return axiosClient.patch(`/semesters/${id}/uncourant`).then((res) => res.data)
}

/**
 * Bascule au semestre suivant : chaque niveau avance d'un semestre (S1→S2,
 * S3→S4…) ; les emplois du temps du semestre quitté sont archivés (salles
 * libérées). Aucune donnée n'est supprimée. Renvoie le récapitulatif.
 */
export function rolloverSemesters() {
  return axiosClient.post('/semesters/rollover').then((res) => res.data)
}

// Aperçu d'impact avant suppression (§ preview) : modules/emplois du temps/examens
// rattachés au semestre qui bloquent sa suppression. Alimente la modale.
export function getSemesterDeletionImpact(id) {
  return axiosClient.get(`/semesters/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteSemester(id, cascade = false) {
  return axiosClient.delete(`/semesters/${id}`, { params: { cascade } })
}
