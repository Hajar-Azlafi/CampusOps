import axiosClient from './axiosClient'

// Import d'emploi du temps par un responsable pédagogique, en DEUX phases
// (cahier des charges §5, §7, §8, §24), distinct de l'import ADMIN historique.
// Le fichier ne contient que des séances ; le contexte (année, filière, niveau,
// promotion, groupe, semestre, session) est choisi dans l'interface et transmis
// en paramètres. La sécurité (accès à la filière) est vérifiée côté backend.

// Construit les paramètres de contexte communs aux trois appels. La fenêtre de
// validité (dateDebut/dateFin) choisie par le RP à l'import n'est transmise que
// si elle est renseignée ; le backend la borne au semestre courant sélectionné.
function contextParams(context = {}) {
  const params = {
    academicYearId: context.academicYearId,
    programId: context.programId,
    levelId: context.levelId,
    promotionId: context.promotionId,
    groupId: context.groupId,
    semesterId: context.semesterId,
    sessionId: context.sessionId,
  }
  if (context.dateDebut) params.dateDebut = context.dateDebut
  if (context.dateFin) params.dateFin = context.dateFin
  return params
}

// Modèle Excel contextualisé (feuilles Séances / Instructions / Valeurs
// autorisées). Renvoie la réponse complète : l'appelant lit `response.data`
// (blob) et le passe à downloadBlob.
export function downloadTimetableTemplate(context) {
  return axiosClient.get('/timetables/import/template', {
    params: contextParams(context),
    responseType: 'blob',
  })
}

// Phase 1 : prévisualisation. Analyse le fichier et renvoie un rapport détaillé
// (compteurs + lignes) SANS rien enregistrer.
export function previewTimetableImport(file, context) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/timetables/import/preview', formData, {
      params: contextParams(context),
      headers: { 'Content-Type': undefined },
    })
    .then((res) => res.data)
}

// Phase 2 : confirmation. N'enregistre les séances que si aucune ligne n'est en
// erreur (le backend rejette sinon, sans rien écrire).
export function confirmTimetableImport(file, context) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/timetables/import/confirm', formData, {
      params: contextParams(context),
      headers: { 'Content-Type': undefined },
    })
    .then((res) => res.data)
}
