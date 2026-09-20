import axiosClient from './axiosClient'

// Import d'un PLANNING D'OCCUPATIONS SUPPLÉMENTAIRES (soutenances ou occupations
// « autre ») en DEUX phases, même mécanique que l'import d'examens et d'emploi du
// temps. Le fichier ne contient que les événements datés ; le contexte —
// catégorie (obligatoire), filière et promotion (facultatives selon la
// catégorie) — est choisi dans l'interface et transmis en paramètres.
//
// Différences avec l'import d'examens :
//   - PAS de semestre ni de session : l'année de rattachement est dérivée de la
//     promotion ou, à défaut, de la date de la ligne.
//   - la catégorie est obligatoire et borne les types acceptés, ce qui interdit
//     d'importer un examen par ce chemin (les examens ont leur propre import).
//   - une salle désactivée est refusée.

// Construit les paramètres de contexte communs aux trois appels. Les valeurs
// vides sont omises : le backend traite « absent » comme « non rattaché ».
function contextParams(context = {}) {
  const params = { categorie: context.categorie }
  if (context.programId != null && context.programId !== '') params.programId = context.programId
  if (context.promotionId != null && context.promotionId !== '') {
    params.promotionId = context.promotionId
  }
  return params
}

// Modèle Excel contextualisé (feuille de saisie / Instructions / Valeurs
// autorisées). Renvoie la réponse complète : l'appelant lit `response.data`
// (blob) et le passe à downloadBlob.
export function downloadOccupationTemplate(context) {
  return axiosClient.get('/occupations/import/template', {
    params: contextParams(context),
    responseType: 'blob',
  })
}

// Phase 1 : prévisualisation. Analyse le fichier et renvoie un rapport détaillé
// (compteurs + lignes) SANS rien enregistrer.
export function previewOccupationImport(file, context) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/occupations/import/preview', formData, {
      params: contextParams(context),
      headers: { 'Content-Type': undefined },
    })
    .then((res) => res.data)
}

// Phase 2 : confirmation. N'enregistre les occupations que si aucune ligne n'est
// en erreur (le backend rejette sinon, sans rien écrire).
export function confirmOccupationImport(file, context) {
  const formData = new FormData()
  formData.append('file', file)
  return axiosClient
    .post('/occupations/import/confirm', formData, {
      params: contextParams(context),
      headers: { 'Content-Type': undefined },
    })
    .then((res) => res.data)
}
