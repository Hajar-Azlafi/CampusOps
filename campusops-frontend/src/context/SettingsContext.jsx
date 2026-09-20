import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { fetchBranding, mediaSrc } from '../api/settingsApi'
import { applyBrandColors, DEFAULT_ACCENT, DEFAULT_PRIMARY } from '../utils/brandColors'
import { formaterDate, formaterDateHeure, formaterHeure } from '../utils/displayFormat'
import { useTheme } from './ThemeContext'

/**
 * Identité visuelle et préférences d'affichage de l'établissement (Module 11).
 *
 * Alimenté par `GET /api/settings/branding`, volontairement public : la page de
 * connexion doit afficher le nom, le logo et les couleurs AVANT qu'un jeton
 * existe. Aucun paramètre métier ne passe par ici.
 *
 * Le provider se contente d'appliquer ce que le serveur annonce :
 *   - couleurs      -> variables CSS du thème existant (cf. utils/brandColors) ;
 *   - thème         -> `applyDefaultTheme` du ThemeProvider, qui ne s'applique
 *                      que si l'utilisateur n'a pas déjà choisi son thème ;
 *   - titre/favicon -> onglet du navigateur ;
 *   - date / heure  -> `formatDate`, `formatHeure`, `formatDateHeure`, déjà liés
 *                      aux motifs configurés (§10) et donc utilisables tels
 *                      quels par les écrans, qui n'ont plus à connaître le
 *                      format retenu par l'établissement ;
 *   - pagination    -> `pagination.activee` / `pagination.taille`, consommés par
 *                      le hook `usePagination`.
 *
 * Il expose aussi un aperçu temporaire (`previewColors`) utilisé par l'onglet
 * « Affichage » pour montrer une couleur en temps réel avant enregistrement.
 */

const SettingsContext = createContext(null)

/** Valeurs de repli : le frontend doit rester utilisable si l'appel échoue. */
const FALLBACK = {
  nom: 'CampusOps',
  nomCourt: 'CampusOps',
  slogan: null,
  ville: null,
  pays: null,
  couleurPrincipale: DEFAULT_PRIMARY,
  couleurSecondaire: DEFAULT_ACCENT,
  themeParDefaut: 'SYSTEME',
  motifDate: 'dd/MM/yyyy',
  motifHeure: 'HH:mm',
  paginationActivee: true,
  elementsParPage: 10,
  logoUrl: null,
  faviconUrl: null,
}

/** Met à jour l'icône de l'onglet ; crée le lien s'il n'existe pas encore. */
function applyFavicon(url) {
  const href = mediaSrc(url)
  if (!href) return
  let link = document.querySelector("link[rel~='icon']")
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.href = href
}

export function SettingsProvider({ children }) {
  const { isDark, applyDefaultTheme } = useTheme()
  const [branding, setBranding] = useState(FALLBACK)
  const [loading, setLoading] = useState(true)
  // Couleurs affichées : celles enregistrées, ou celles d'un aperçu en cours.
  const [preview, setPreview] = useState(null)

  const load = useCallback(() => {
    setLoading(true)
    return fetchBranding()
      .then((data) => {
        setBranding({ ...FALLBACK, ...data })
        return data
      })
      .catch(() => {
        // Backend injoignable ou hors ligne : on garde l'identité de repli.
        setBranding(FALLBACK)
        return null
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  // Couleurs : réappliquées à chaque bascule de thème, car la variante « texte »
  // de l'accent et le traitement des titres diffèrent en clair et en sombre.
  const principale = preview?.principale ?? branding.couleurPrincipale
  const secondaire = preview?.secondaire ?? branding.couleurSecondaire

  useEffect(() => {
    applyBrandColors({ principale, secondaire, isDark })
  }, [principale, secondaire, isDark])

  // Thème par défaut de l'établissement : n'écrase jamais un choix personnel.
  useEffect(() => {
    if (branding.themeParDefaut === 'CLAIR') applyDefaultTheme('light')
    else if (branding.themeParDefaut === 'SOMBRE') applyDefaultTheme('dark')
  }, [branding.themeParDefaut, applyDefaultTheme])

  // Onglet du navigateur : nom de l'établissement + favicon configuré.
  useEffect(() => {
    document.title = branding.nom ? `${branding.nom} · CampusOps` : 'CampusOps'
    applyFavicon(branding.faviconUrl)
  }, [branding.nom, branding.faviconUrl])

  const previewColors = useCallback((principaleApercu, secondaireApercu) => {
    setPreview({ principale: principaleApercu, secondaire: secondaireApercu })
  }, [])

  const resetPreview = useCallback(() => setPreview(null), [])

  const value = useMemo(
    () => ({
      branding,
      loading,
      /** À appeler après un enregistrement des paramètres pour tout rafraîchir. */
      refresh: load,
      previewColors,
      resetPreview,
      logoSrc: mediaSrc(branding.logoUrl),
      faviconSrc: mediaSrc(branding.faviconUrl),
      /** Raccourci d'affichage : nom court si défini, sinon nom complet. */
      nomAffiche: branding.nomCourt || branding.nom,
      // Formateurs déjà liés aux motifs configurés : un écran écrit
      // `formatDateHeure(valeur)` sans savoir quel format est en vigueur.
      formatDate: (valeur) => formaterDate(valeur, branding.motifDate),
      formatHeure: (valeur) => formaterHeure(valeur, branding.motifHeure),
      formatDateHeure: (valeur) =>
        formaterDateHeure(valeur, branding.motifDate, branding.motifHeure),
      /** Pagination configurée (§10), lue par le hook `usePagination`. */
      pagination: {
        activee: branding.paginationActivee !== false,
        taille: branding.elementsParPage > 0 ? branding.elementsParPage : 10,
      },
    }),
    [branding, loading, load, previewColors, resetPreview]
  )

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
}

export function useSettings() {
  const context = useContext(SettingsContext)
  if (!context) throw new Error('useSettings doit être utilisé dans un SettingsProvider')
  return context
}
