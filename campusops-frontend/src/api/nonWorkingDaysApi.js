import axiosClient from './axiosClient'

/**
 * Calendrier des jours non ouvrables (§4). L'ecriture est reservee a l'ADMIN
 * cote backend ; la lecture sert aussi a expliquer une fermeture. `dateFin` est
 * facultative (periode de vacances) : par defaut elle vaut `dateDebut`.
 */
export function fetchNonWorkingDays({ actif, debut, fin } = {}) {
  const params = {}
  if (actif !== undefined && actif !== '') params.actif = actif
  if (debut) params.debut = debut
  if (fin) params.fin = fin
  return axiosClient.get('/non-working-days', { params }).then((res) => res.data)
}

export function fetchNonWorkingDaysByYear(annee) {
  return axiosClient.get(`/non-working-days/year/${annee}`).then((res) => res.data)
}

export function getNonWorkingDay(id) {
  return axiosClient.get(`/non-working-days/${id}`).then((res) => res.data)
}

export function createNonWorkingDay(payload) {
  return axiosClient.post('/non-working-days', payload).then((res) => res.data)
}

export function updateNonWorkingDay(id, payload) {
  return axiosClient.put(`/non-working-days/${id}`, payload).then((res) => res.data)
}

export function activateNonWorkingDay(id) {
  return axiosClient.patch(`/non-working-days/${id}/activate`)
}

export function deactivateNonWorkingDay(id) {
  return axiosClient.patch(`/non-working-days/${id}/deactivate`)
}

export function deleteNonWorkingDay(id) {
  return axiosClient.delete(`/non-working-days/${id}`)
}
