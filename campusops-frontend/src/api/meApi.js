import axiosClient from './axiosClient'

// Renvoie le profil et le perimetre de l'utilisateur connecte :
// { id, firstName, lastName, email, role, admin, programs: [...] }.
// Pour un RESPONSABLE_PEDAGOGIQUE, `programs` liste uniquement ses filieres.
export function getMyScope() {
  return axiosClient.get('/me/scope').then((res) => res.data)
}
