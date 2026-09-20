import { useMemo } from 'react'
import { useSettings } from '../context/SettingsContext'

/**
 * Découpage en pages piloté par les paramètres de l'établissement
 * (Module 11, §10 « pagination activée » / « nombre d'éléments par page »).
 *
 * Les huit écrans qui paginaient jusqu'ici déclaraient chacun leur propre
 * `const PAGE_SIZE = 10` : la valeur configurée dans l'onglet Affichage était
 * donc décorative (§20 « aucune valeur codée en dur »). Ce hook est désormais
 * l'unique endroit qui lit ces deux paramètres.
 *
 * Pagination désactivée : tout est rendu sur une seule page et la barre de
 * navigation disparaît — c'est le sens littéral du paramètre, et non une
 * pagination silencieusement remise à dix.
 *
 * L'index de page reste porté par l'écran appelant (`useState`), qui le remet
 * déjà à 1 au bon moment (rechargement, changement de filtre) : le hook ne
 * change donc ni l'ordre des déclarations ni les dépendances existantes.
 *
 * @param {Array} items liste complète, déjà filtrée et triée par l'appelant.
 * @param {number} page index de page courant, 1-based.
 */
const VIDE = []

export function usePagination(items, page) {
  const { pagination } = useSettings()

  const liste = Array.isArray(items) ? items : VIDE
  // Zéro signifie « pas de découpage » : pagination désactivée par l'admin.
  const taille = pagination.activee ? pagination.taille : 0
  const totalPages = taille > 0 ? Math.max(1, Math.ceil(liste.length / taille)) : 1

  // Bornage au rendu plutôt que dans un effet : quand un filtre réduit la
  // liste, ou quand l'admin augmente le nombre d'éléments par page, la page
  // courante peut dépasser la dernière page. On corrige la valeur qui sert à
  // la découpe, sans second rendu ni tableau vide passager.
  const pageAffichee = Math.min(Math.max(1, page || 1), totalPages)

  const pageItems = useMemo(
    () => (taille > 0 ? liste.slice((pageAffichee - 1) * taille, pageAffichee * taille) : liste),
    [liste, pageAffichee, taille]
  )

  return {
    /** Page réellement affichée (déjà bornée à [1, totalPages]). */
    pageAffichee,
    totalPages,
    /** Tranche à rendre. Liste entière si la pagination est désactivée. */
    pageItems,
    /** Vrai s'il y a plus d'une page : condition d'affichage de la barre. */
    afficher: taille > 0 && liste.length > taille,
    /** Nombre d'éléments par page effectivement appliqué (0 si désactivée). */
    taille,
  }
}
