import axiosClient from './axiosClient'

// Modules d'enseignement rattachés à un contexte pédagogique (filière + semestre).
// Le backend borne automatiquement au périmètre du demandeur : ADMIN → tous les
// modules ; responsable pédagogique → uniquement ceux de ses filières.
export function fetchModules({ programId, semesterId, actif } = {}) {
  const params = {}
  if (programId !== undefined && programId !== '' && programId !== null) params.programId = programId
  if (semesterId !== undefined && semesterId !== '' && semesterId !== null) params.semesterId = semesterId
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/modules', { params }).then((res) => res.data)
}

export function getModule(id) {
  return axiosClient.get(`/modules/${id}`).then((res) => res.data)
}

export function createModule(payload) {
  return axiosClient.post('/modules', payload).then((res) => res.data)
}

export function updateModule(id, payload) {
  return axiosClient.put(`/modules/${id}`, payload).then((res) => res.data)
}

export function deactivateModule(id) {
  return axiosClient.patch(`/modules/${id}/deactivate`)
}

export function activateModule(id) {
  return axiosClient.patch(`/modules/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : séances/examens qui utilisent
// ce module et bloquent sa suppression (« module utilisé dans un EDT »).
export function getModuleDeletionImpact(id) {
  return axiosClient.get(`/modules/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteModule(id, cascade = false) {
  return axiosClient.delete(`/modules/${id}`, { params: { cascade } })
}
