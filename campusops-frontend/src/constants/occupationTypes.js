/**
 * Occupations supplémentaires : catégories (= onglets) et types.
 *
 * Miroir exact des enums backend `OccupationCategorie` et `OccupationType`.
 * Les libellés reprennent `OccupationType.getLibelle()` afin qu'un même type
 * s'affiche à l'identique dans une liste, un formulaire ou un modèle Excel.
 */

/** Catégorie d'une occupation. Chaque catégorie correspond à un onglet du module. */
export const OCCUPATION_CATEGORIES = {
  EXAMEN: 'EXAMEN',
  SOUTENANCE: 'SOUTENANCE',
  AUTRE: 'AUTRE',
}

/**
 * Onglets du module, dans l'ordre d'affichage. Le `slug` est la valeur portée par
 * le paramètre d'URL `?onglet=` — c'est aussi la convention utilisée par les liens
 * profonds des notifications d'import côté backend.
 */
export const OCCUPATION_TABS = [
  {
    slug: 'examens',
    categorie: OCCUPATION_CATEGORIES.EXAMEN,
    label: 'Planning examens',
  },
  {
    slug: 'soutenances',
    categorie: OCCUPATION_CATEGORIES.SOUTENANCE,
    label: 'Planning soutenances',
  },
  {
    slug: 'autre',
    categorie: OCCUPATION_CATEGORIES.AUTRE,
    label: 'Autre',
  },
]

export const DEFAULT_OCCUPATION_TAB = OCCUPATION_TABS[0].slug

/** Onglet correspondant à un slug d'URL, ou l'onglet par défaut si inconnu. */
export function occupationTabBySlug(slug) {
  return OCCUPATION_TABS.find((tab) => tab.slug === slug) ?? OCCUPATION_TABS[0]
}

/** Slug d'URL d'une catégorie (utile pour construire un lien vers un onglet). */
export function occupationTabSlug(categorie) {
  return OCCUPATION_TABS.find((tab) => tab.categorie === categorie)?.slug ?? DEFAULT_OCCUPATION_TAB
}

/** Types d'occupation, avec leur catégorie de rattachement. */
export const OCCUPATION_TYPES = [
  { value: 'EXAMEN', label: 'Examen', categorie: OCCUPATION_CATEGORIES.EXAMEN },
  { value: 'SOUTENANCE', label: 'Soutenance', categorie: OCCUPATION_CATEGORIES.SOUTENANCE },
  { value: 'EVENEMENT', label: 'Événement', categorie: OCCUPATION_CATEGORIES.AUTRE },
  { value: 'REUNION', label: 'Réunion', categorie: OCCUPATION_CATEGORIES.AUTRE },
  {
    value: 'ACTIVITE_PEDAGOGIQUE',
    label: 'Activité pédagogique',
    categorie: OCCUPATION_CATEGORIES.AUTRE,
  },
  { value: 'ACTIVITE_CLUB', label: 'Activité de club', categorie: OCCUPATION_CATEGORIES.AUTRE },
  { value: 'CONFERENCE', label: 'Conférence', categorie: OCCUPATION_CATEGORIES.AUTRE },
  { value: 'AUTRE', label: 'Autre activité', categorie: OCCUPATION_CATEGORIES.AUTRE },
]

/** Types proposables pour une catégorie, dans l'ordre de déclaration. */
export function occupationTypesOf(categorie) {
  return OCCUPATION_TYPES.filter((type) => type.categorie === categorie)
}

export function occupationTypeLabel(value) {
  return OCCUPATION_TYPES.find((type) => type.value === value)?.label ?? value
}
