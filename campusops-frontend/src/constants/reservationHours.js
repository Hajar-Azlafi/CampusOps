// Règles horaires des réservations (miroir côté client de la configuration
// métier centralisée du backend : campusops.reservation).
// Le backend reste la source de vérité ; cette validation améliore seulement
// l'expérience utilisateur en évitant des allers-retours réseau inutiles.
//
// Valeurs de repli utilisées tant que `GET /api/availability/window` n'a pas
// répondu : plage du lundi au samedi, de 08h30 à 18h00, dimanche fermé.

export const OPENING_TIME = '08:30'
export const CLOSING_TIME = '18:00'
export const SATURDAY_OPEN = true
export const SUNDAY_OPEN = false

// Durée minimale exploitable d'un créneau (repli ; le serveur annonce la vraie
// valeur via `dureeMinimaleMinutes`). Un créneau plus court n'est ni affiché ni
// réservable : il évite les « 10:25 → 10:35 » absurdes.
export const MIN_SLOT_MINUTES = 30

// Durées proposées par le sélecteur « durée souhaitée » (repli).
export const DURATION_OPTIONS = [60, 90, 120, 180]

// Convertit une chaîne "HH:mm" en nombre de minutes depuis minuit.
export function toMinutes(hm) {
  if (!hm || typeof hm !== 'string' || !hm.includes(':')) return null
  const [h, m] = hm.split(':').map(Number)
  if (Number.isNaN(h) || Number.isNaN(m)) return null
  return h * 60 + m
}

// Renvoie le jour de la semaine (0 = dimanche … 6 = samedi) pour une date "yyyy-MM-dd".
function dayOfWeek(dateStr) {
  if (!dateStr) return null
  const d = new Date(dateStr + 'T00:00:00')
  if (Number.isNaN(d.getTime())) return null
  return d.getDay()
}

/**
 * Libellé lisible d'une durée en minutes : 60 → « 1 h », 90 → « 1 h 30 »,
 * 45 → « 45 min ». Utilisé par le sélecteur de durée et les messages d'erreur.
 */
export function formatDuree(minutes) {
  const total = Number(minutes)
  if (!Number.isFinite(total) || total <= 0) return ''
  const h = Math.floor(total / 60)
  const m = total % 60
  if (h === 0) return `${m} min`
  if (m === 0) return `${h} h`
  return `${h} h ${String(m).padStart(2, '0')}`
}

/**
 * Valide une réservation côté client. Renvoie un message d'erreur en français
 * si les règles ne sont pas respectées, ou null si tout est valide.
 *
 * `options` permet d'utiliser les bornes réellement annoncées par le serveur
 * (horaires dérivés des créneaux officiels, durée minimale exploitable) plutôt
 * que les valeurs de repli ci-dessus :
 *   { opening, closing, minSlotMinutes }
 */
export function validateReservationHours(date, heureDebut, heureFin, options = {}) {
  const debut = toMinutes(heureDebut)
  const fin = toMinutes(heureFin)
  const openingHm = options.opening || OPENING_TIME
  const closingHm = options.closing || CLOSING_TIME
  const opening = toMinutes(openingHm)
  const closing = toMinutes(closingHm)
  const minSlot = Number.isFinite(Number(options.minSlotMinutes))
    ? Number(options.minSlotMinutes)
    : MIN_SLOT_MINUTES

  if (debut == null || fin == null) {
    return 'Veuillez renseigner une heure de début et une heure de fin valides.'
  }
  if (fin <= debut) {
    return "L'heure de fin doit être postérieure à l'heure de début."
  }

  const dow = dayOfWeek(date)
  if (dow === 0 && !SUNDAY_OPEN) {
    return 'Les réservations ne sont pas autorisées le dimanche.'
  }
  if (dow === 6 && !SATURDAY_OPEN) {
    return 'Les réservations ne sont pas autorisées le samedi.'
  }

  if (debut < opening || fin > closing) {
    return `Les réservations sont autorisées uniquement entre ${openingHm} et ${closingHm}.`
  }

  // Micro-créneaux : une réservation plus courte que la durée minimale n'est pas
  // exploitable (même règle que le moteur de disponibilité côté serveur).
  if (minSlot > 0 && fin - debut < minSlot) {
    return `Un créneau doit durer au moins ${formatDuree(minSlot)} : une réservation plus courte n'est pas exploitable.`
  }

  return null
}

/**
 * Valide la date d'une recherche de disponibilité (§1) avec les bornes
 * annoncées par le serveur : `dateMin` (jamais dans le passé, cutoff du jour
 * appliqué) et `dateMax` (fin de l'année universitaire active, éventuellement
 * absente). Renvoie un message d'erreur ou null.
 */
export function validateSearchDate(date, window) {
  if (!date) return 'Veuillez choisir une date.'
  if (!window) return null
  if (window.exploitable === false) {
    return window.messageConfiguration || "Aucune date n'est ouverte à la recherche."
  }
  if (window.dateMin && date < window.dateMin) {
    const limite = window.libelleDateMin || window.dateMin
    return window.limiteJourneeDepassee
      ? `Les recherches pour aujourd'hui sont closes depuis ${window.heureLimiteRecherche}. La première date disponible est le ${limite}.`
      : `Impossible de rechercher une date passée. La première date disponible est le ${limite}.`
  }
  if (window.dateMax && date > window.dateMax) {
    const limite = window.libelleDateMax || window.dateMax
    const annee = window.anneeActiveLibelle ? ` (${window.anneeActiveLibelle})` : ''
    return `Cette date dépasse l'année universitaire active${annee}. La dernière date disponible est le ${limite}.`
  }
  return null
}
