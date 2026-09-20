import axiosClient from './axiosClient'

export function fetchTimeSlots({ actif } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  return axiosClient.get('/time-slots', { params }).then((res) => res.data)
}

export function getTimeSlot(id) {
  return axiosClient.get(`/time-slots/${id}`).then((res) => res.data)
}

export function createTimeSlot(payload) {
  return axiosClient.post('/time-slots', payload).then((res) => res.data)
}

export function updateTimeSlot(id, payload) {
  return axiosClient.put(`/time-slots/${id}`, payload).then((res) => res.data)
}

export function deactivateTimeSlot(id) {
  return axiosClient.patch(`/time-slots/${id}/deactivate`)
}

export function activateTimeSlot(id) {
  return axiosClient.patch(`/time-slots/${id}/activate`)
}

// Aperçu d'impact avant suppression (§ preview) : séances/examens qui utilisent
// ce créneau et bloquent sa suppression. Alimente la modale de confirmation.
export function getTimeSlotDeletionImpact(id) {
  return axiosClient.get(`/time-slots/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteTimeSlot(id, cascade = false) {
  return axiosClient.delete(`/time-slots/${id}`, { params: { cascade } })
}

// Réorganise les créneaux selon la liste ordonnée d'identifiants (§2/§3).
// Renvoie la liste mise à jour (triée par ordre croissant).
export function reorderTimeSlots(orderedIds) {
  return axiosClient.put('/time-slots/reorder', orderedIds).then((res) => res.data)
}
