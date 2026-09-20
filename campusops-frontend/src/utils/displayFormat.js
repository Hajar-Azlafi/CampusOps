// Formatage des dates et des heures selon les paramètres de l'établissement
// (Module 11, §10 « format de date » / « format d'heure »).
//
// Le backend n'envoie pas un nom d'énumération mais directement le motif
// `DateTimeFormatter` correspondant (`motifDate`, `motifHeure` de
// /api/settings/branding) : côté React on n'interprète donc que les cinq motifs
// réellement proposés dans l'onglet Affichage, sans embarquer de bibliothèque de
// dates supplémentaire (§23 : pas de dépendance inutile).
//
//   dd/MM/yyyy · yyyy-MM-dd · MM/dd/yyyy      (DateDisplayFormat)
//   HH:mm      · hh:mm a                      (TimeDisplayFormat)
//
// Toute valeur illisible est rendue telle quelle plutôt que remplacée par
// « Invalid Date » : un tableau ne doit jamais devenir inintelligible à cause
// d'un format.

export const MOTIF_DATE_DEFAUT = 'dd/MM/yyyy'
export const MOTIF_HEURE_DEFAUT = 'HH:mm'

/**
 * Découpe une valeur ISO en ses composants, sans passer par `Date` : on évite
 * ainsi tout décalage de fuseau sur une date seule (`2026-09-05` interprété en
 * UTC puis affiché en local peut reculer d'un jour).
 */
function morceaux(valeur) {
  if (valeur == null || valeur === '') return null
  const texte = String(valeur)

  // « 2026-09-05T14:30[:00] » ou « 2026-09-05 14:30 » ou « 2026-09-05 »
  const dateTime = texte.match(/^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2}))?/)
  if (dateTime) {
    const [, annee, mois, jour, heures, minutes] = dateTime
    return { annee, mois, jour, heures: heures ?? null, minutes: minutes ?? null }
  }

  // « 14:30[:00] » : heure seule, telle que la renvoient les créneaux.
  const heureSeule = texte.match(/^(\d{2}):(\d{2})/)
  if (heureSeule) {
    const [, heures, minutes] = heureSeule
    return { annee: null, mois: null, jour: null, heures, minutes }
  }

  return null
}

/** Applique un motif de date aux composants extraits. */
function rendreDate(parts, motif) {
  if (!parts?.annee) return null
  switch (motif) {
    case 'yyyy-MM-dd':
      return `${parts.annee}-${parts.mois}-${parts.jour}`
    case 'MM/dd/yyyy':
      return `${parts.mois}/${parts.jour}/${parts.annee}`
    case 'dd/MM/yyyy':
    default:
      return `${parts.jour}/${parts.mois}/${parts.annee}`
  }
}

/** Applique un motif d'heure aux composants extraits. */
function rendreHeure(parts, motif) {
  if (!parts?.heures) return null
  if (motif === 'hh:mm a') {
    const h = Number(parts.heures)
    const suffixe = h < 12 ? 'AM' : 'PM'
    const douze = h % 12 === 0 ? 12 : h % 12
    return `${String(douze).padStart(2, '0')}:${parts.minutes} ${suffixe}`
  }
  return `${parts.heures}:${parts.minutes}`
}

/** Date seule, ou la valeur d'origine si elle n'est pas exploitable. */
export function formaterDate(valeur, motif = MOTIF_DATE_DEFAUT) {
  const parts = morceaux(valeur)
  return rendreDate(parts, motif) ?? (valeur == null ? '' : String(valeur))
}

/** Heure seule, ou la valeur d'origine si elle n'est pas exploitable. */
export function formaterHeure(valeur, motif = MOTIF_HEURE_DEFAUT) {
  const parts = morceaux(valeur)
  return rendreHeure(parts, motif) ?? (valeur == null ? '' : String(valeur))
}

/**
 * Date puis heure, séparées par une espace. Si la valeur ne porte pas d'heure,
 * seule la date est rendue (et inversement).
 */
export function formaterDateHeure(
  valeur,
  motifDate = MOTIF_DATE_DEFAUT,
  motifHeure = MOTIF_HEURE_DEFAUT
) {
  const parts = morceaux(valeur)
  if (!parts) return valeur == null ? '' : String(valeur)
  const date = rendreDate(parts, motifDate)
  const heure = rendreHeure(parts, motifHeure)
  return [date, heure].filter(Boolean).join(' ')
}
