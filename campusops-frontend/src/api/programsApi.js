import axiosClient from './axiosClient'

// Liste les filieres. `actif` filtre cote backend : true = actives (defaut
// pour toute selection operationnelle), false = desactivees, undefined = toutes
// (ecran d'administration « Tous »).
export function fetchPrograms(params = {}) {
  return axiosClient.get('/programs', { params }).then((res) => res.data)
}

export function searchPrograms(keyword) {
  return axiosClient.get('/programs/search', { params: { keyword } }).then((res) => res.data)
}

export function getProgram(id) {
  return axiosClient.get(`/programs/${id}`).then((res) => res.data)
}

// `actif` filtre les filieres du departement (voir fetchPrograms).
export function getProgramsByDepartment(departmentId, params = {}) {
  return axiosClient
    .get(`/programs/department/${departmentId}`, { params })
    .then((res) => res.data)
}

export function createProgram(payload) {
  return axiosClient.post('/programs', payload).then((res) => res.data)
}

export function updateProgram(id, payload) {
  return axiosClient.put(`/programs/${id}`, payload).then((res) => res.data)
}

// Aperçu d'impact avant suppression (§ preview) : promotions/groupes/modules
// supprimés en cascade et/ou éléments bloquants. Alimente la modale.
export function getProgramDeletionImpact(id) {
  return axiosClient.get(`/programs/${id}/impact-suppression`).then((res) => res.data)
}

export function deleteProgram(id, cascade = false) {
  return axiosClient.delete(`/programs/${id}`, { params: { cascade } })
}

// Desactive une filiere (soft-disable, cascade descendante cote backend).
export function deactivateProgram(id) {
  return axiosClient.patch(`/programs/${id}/deactivate`)
}

// Reactive une filiere (sans reactivation aveugle des enfants, §16).
export function activateProgram(id) {
  return axiosClient.patch(`/programs/${id}/activate`)
}

// Compteurs d'impact avant desactivation (promotions/groupes qui deviendront
// indisponibles), pour la confirmation frontend (§17).
export function getProgramDeactivationImpact(id) {
  return axiosClient.get(`/programs/${id}/impact-desactivation`).then((res) => res.data)
}

// Affecte (ou retire) le responsable pedagogique d'une filiere. Un userId nul
// retire le responsable actuel. Reserve a l'administrateur (controle backend).
export function assignResponsable(programId, userId) {
  return axiosClient
    .patch(`/programs/${programId}/responsable`, { userId: userId ?? null })
    .then((res) => res.data)
}

// Liste les filieres dont l'utilisateur donne est responsable pedagogique.
export function getProgramsByResponsable(userId) {
  return axiosClient.get(`/programs/responsable/${userId}`).then((res) => res.data)
}
