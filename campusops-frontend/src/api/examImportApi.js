import axiosClient from './axiosClient'

// Import d'un PLANNING D'EXAMENS par un responsable pédagogique, en DEUX phases
// (même mécanique que l'import d'emploi du temps). Le fichier ne contient que
// des examens (chaque ligne est un évènement daté) ; le contexte (année,
// filière, promotion, semestre, session) est choisi dans l'interface et transmis
// en paramètres. La sécurité (accès à la filière) est vérifiée côté backend.
//
// Différences avec l'import d'emploi du temps :
//   - PAS de niveau ni de groupe dans le contexte : le niveau est porté par la
//     promotion, et le groupe est facultatif et saisi ligne par ligne dans le
//     fichier (vide = toute la promotion).
//   - PAS de fenêtre de validité (dateDebut/dateFin) : les examens sont datés
//     ligne par ligne ; chaque date est bornée au semestre côté backend.

// Construit les paramètres de contexte communs aux trois appels.
function contextParams(context = {}) {
  return {
    academicYearId: context.academicYearId,
    programId: context.programId,
    promotionId: context.promotionId,
    semesterId: context.semesterId,
    sessionId: context.sessionId,
  }
}

// Modèle Excel contextualisé (feuilles Examens / Instructions / Valeurs
// autorisées). Renvoie la réponse complète : l'appelant lit `response.data`
// (blob) et le passe à downloadBlob.
export function downloadExamTemplate(context) {
  return axiosClient.get('/examens/import/template', {
    params: contextParams(context),
    responseType: 'blob',
  })
}

// Phase 1 : prévisualisation. Analyse le fichier et renvoie un rapport détaillé
// (compteurs + lignes) SANS rien enregistrer.
export function previewExamImport(file, context) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/examens/import/preview', formData, {
      params: contextParams(context),
      headers: { 'Content-Type': undefined },
    })
    .then((res) => res.data)
}

// Phase 2 : confirmation. N'enregistre les examens que si aucune ligne n'est en
// erreur (le backend rejette sinon, sans rien écrire).
export function confirmExamImport(file, context) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/examens/import/confirm', formData, {
      params: contextParams(context),
      headers: { 'Content-Type': undefined },
    })
    .then((res) => res.data)
}
