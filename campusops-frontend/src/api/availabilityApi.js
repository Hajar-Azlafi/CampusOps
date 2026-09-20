import axiosClient from './axiosClient'

/**
 * Regles de saisie de la recherche, calculees cote serveur (§1, §3, §4, §6).
 * A appeler au chargement du formulaire pour le borner :
 *   { aujourdHui, dateMin, dateMax, libelleDateMin, libelleDateMax, dateParDefaut,
 *     heureLimiteRecherche, limiteJourneeDepassee, heureOuverture, heureFermeture,
 *     dureeMinimaleMinutes, dureesProposees: [60, 90, ...],
 *     creneaux: [{ id, nom, heureDebut, heureFin, label, dureeMinutes }],
 *     anneeActiveId, anneeActiveLibelle, anneeActiveDebut, anneeActiveFin,
 *     messageConfiguration, exploitable }
 *
 * Le client ne recalcule aucune de ces regles : il borne son formulaire avec ce
 * que le serveur annonce, et le serveur revalide de toute facon a chaque
 * recherche puis a chaque reservation (§2, §19).
 */
export function getSearchWindow() {
  return axiosClient.get('/availability/window').then((res) => res.data)
}

/**
 * Recherche des espaces disponibles selon des criteres complets.
 * criteria: { date, heureDebut, heureFin, dureeMinutes, buildingId, floorId,
 *             type, capaciteMin, equipmentIds, exactSpaceQuery }
 * Seule `date` est obligatoire.
 *  - heures OPTIONNELLES (§2) : sans elles, le backend calcule les vraies
 *    periodes libres de la journee entiere ;
 *  - `dureeMinutes` OPTIONNELLE (§6/§7) : ne garde que les espaces offrant un
 *    bloc libre CONTINU d'au moins cette duree.
 *
 * Toutes les fonctions ci-dessous renvoient l'enveloppe
 * `AvailabilitySearchResponseDto` (et non un simple tableau) :
 *   { date, libelleDate, mode, jourOuvrable, messageFermeture, typeFermeture,
 *     dateValide, messageDate, dateMin, dateMax, heureLimiteRecherche,
 *     limiteJourneeDepassee, messageConfiguration,
 *     heureOuverture, heureFermeture, heureDebutRecherche, heureFinRecherche,
 *     fenetreJourDebut, fenetreJourFin, messageValidation,
 *     dureeSouhaiteeMinutes, dureeMinimaleMinutes,
 *     anneeUniversitaireId, anneeUniversitaireLibelle, anneeUniversitaireResolue,
 *     messageAnneeUniversitaire, anneeActiveId, anneeActiveLibelle,
 *     anneeActiveDebut, anneeActiveFin, total, espaces: [...] }
 * Les appelants doivent lire `response.espaces` pour la liste des espaces.
 */
export function searchAvailability(criteria) {
  return axiosClient.post('/availability/search', criteria).then((res) => res.data)
}

export function searchAvailabilityByBuilding(buildingId, { date, heureDebut, heureFin } = {}) {
  const params = {}
  if (date) params.date = date
  if (heureDebut) params.heureDebut = heureDebut
  if (heureFin) params.heureFin = heureFin
  return axiosClient
    .get(`/availability/building/${buildingId}`, { params })
    .then((res) => res.data)
}

export function searchAvailabilityByFloor(floorId, { date, heureDebut, heureFin } = {}) {
  const params = {}
  if (date) params.date = date
  if (heureDebut) params.heureDebut = heureDebut
  if (heureFin) params.heureFin = heureFin
  return axiosClient
    .get(`/availability/floor/${floorId}`, { params })
    .then((res) => res.data)
}

export function getAvailableToday() {
  return axiosClient.get('/availability/today').then((res) => res.data)
}

export function getAvailableNow() {
  return axiosClient.get('/availability/now').then((res) => res.data)
}
