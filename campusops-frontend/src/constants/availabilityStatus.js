// Rendu de l'etat de disponibilite d'un espace (§13).
//
// Le backend (moteur central de disponibilite) renvoie pour chaque espace un
// champ `statut` a trois valeurs possibles : DISPONIBLE, EN_ATTENTE ou
// INDISPONIBLE. On ne montre JAMAIS un simple « Disponible » pour un espace qui
// ne porte qu'une demande en attente : l'etat doit rester visuellement distinct
// et le bouton d'action doit changer (§13). Ce module centralise ce mapping pour
// que la page de recherche et la fiche detail restent coherentes.

export const AVAIL_STATUS = {
  DISPONIBLE: {
    key: 'DISPONIBLE',
    label: 'Disponible',
    // Vert : reservable immediatement.
    badgeClass: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    dotClass: 'bg-emerald-500',
    // Le bouton d'action reste une reservation classique.
    actionLabel: 'Réserver',
    canReserve: true,
  },
  EN_ATTENTE: {
    key: 'EN_ATTENTE',
    label: 'Demande en attente',
    // Ambre : une demande concurrente existe deja sur ce creneau.
    badgeClass: 'bg-amber-50 text-amber-700 border-amber-200',
    dotClass: 'bg-amber-500',
    // Bouton distinct : l'utilisateur peut deposer une demande concurrente, mais
    // il est prevenu qu'une autre est deja en attente (arbitrage par priorite).
    actionLabel: 'Demande en attente',
    canReserve: true,
  },
  INDISPONIBLE: {
    key: 'INDISPONIBLE',
    label: 'Indisponible',
    // Rouge : creneau bloque (emploi du temps, examen ou reservation acceptee).
    // N'apparait que lors d'une recherche ciblant un espace precis.
    badgeClass: 'bg-red-50 text-red-700 border-red-200',
    dotClass: 'bg-red-500',
    actionLabel: null,
    canReserve: false,
  },
}

/** Configuration d'affichage pour l'etat d'un espace (repli : DISPONIBLE). */
export function availStatus(space) {
  if (!space) return AVAIL_STATUS.DISPONIBLE
  return AVAIL_STATUS[space.statut] || AVAIL_STATUS.DISPONIBLE
}

// Libelle de disponibilite pret a afficher (§15). Le backend fournit
// `affichageDisponibilite` selon le mode (maintenant / aujourd'hui /
// personnalisee) ; on n'ajoute plus jamais « Libre le reste de la journee ».
// Repli raisonnable a partir des periodes libres si le champ est absent.
export function availAffichage(space) {
  if (!space) return ''
  if (space.affichageDisponibilite) return space.affichageDisponibilite
  if (Array.isArray(space.periodesLibres) && space.periodesLibres.length > 0) {
    return space.periodesLibres.map((p) => p.label).join(' · ')
  }
  return ''
}
