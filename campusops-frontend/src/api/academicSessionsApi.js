import axiosClient from './axiosClient'

// Sessions universitaires configurables (cahier des charges §4) : « Session
// normale », « Session de rattrapage », etc. Concept distinct du « type de
// séance ». Lecture ouverte à tout utilisateur authentifié ; les écritures sont
// réservées à l'ADMIN côté backend.

export function fetchSessions({ actif } = {}) {
  const params = {}
  if (actif !== undefined) params.actif = actif
  return axiosClient.get('/academic-sessions', { params }).then((res) => res.data)
}

export function getSession(id) {
  return axiosClient.get(`/academic-sessions/${id}`).then((res) => res.data)
}

export function createSession(payload) {
  return axiosClient.post('/academic-sessions', payload).then((res) => res.data)
}

export function updateSession(id, payload) {
  return axiosClient.put(`/academic-sessions/${id}`, payload).then((res) => res.data)
}

export function deactivateSession(id) {
  return axiosClient.patch(`/academic-sessions/${id}/deactivate`)
}

export function activateSession(id) {
  return axiosClient.patch(`/academic-sessions/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : emplois du temps/examens qui
// utilisent cette session et bloquent sa suppression. Alimente la modale.
export function getSessionDeletionImpact(id) {
  return axiosClient.get(`/academic-sessions/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteSession(id, cascade = false) {
  return axiosClient.delete(`/academic-sessions/${id}`, { params: { cascade } })
}
