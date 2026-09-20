import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'

/**
 * Gestion globale du thème clair / sombre.
 *
 * Le thème n'est PAS géré page par page : le provider pose simplement la classe
 * `dark` sur <html>, et `src/index.css` redéfinit sous `html.dark` les variables
 * de couleur (--color-paper, --color-surface, --color-ink, ...) que consomment
 * tous les utilitaires Tailwind de l'application. Ajouter une page ne demande
 * donc aucun travail supplémentaire, à condition d'utiliser les tokens du thème.
 *
 * Le choix est mémorisé dans localStorage et réappliqué avant le premier rendu
 * par le script inline de `index.html` (aucun flash de thème au rechargement).
 */

const STORAGE_KEY = 'campusops.theme'
const DARK = 'dark'
const LIGHT = 'light'

const ThemeContext = createContext(null)

function readStoredTheme() {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === DARK || stored === LIGHT ? stored : null
  } catch {
    // Navigation privée / stockage indisponible : on retombe sur le système.
    return null
  }
}

function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false
}

function resolveInitialTheme() {
  return readStoredTheme() ?? (systemPrefersDark() ? DARK : LIGHT)
}

/** Applique le thème au document (classe + color-scheme des contrôles natifs). */
function applyTheme(theme) {
  const root = document.documentElement
  root.classList.toggle(DARK, theme === DARK)
  root.dataset.theme = theme
  root.style.colorScheme = theme
}

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(resolveInitialTheme)

  // Applique le thème au document à chaque changement. La mémorisation, elle,
  // n'a lieu que sur un choix explicite (voir setTheme) : tant que rien n'est
  // stocké, l'application continue de suivre la préférence du système.
  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  // Suit les préférences système tant que l'utilisateur n'a pas choisi.
  useEffect(() => {
    if (!window.matchMedia) return
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (event) => {
      if (readStoredTheme() === null) setThemeState(event.matches ? DARK : LIGHT)
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  // Garde les onglets ouverts synchronisés.
  useEffect(() => {
    const onStorage = (event) => {
      if (event.key !== STORAGE_KEY) return
      if (event.newValue === DARK || event.newValue === LIGHT) setThemeState(event.newValue)
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  // Bascule demandée par l'utilisateur : on mémorise le choix (il survit au
  // rechargement et est relu par le script inline de index.html) et on applique
  // un fondu court sur les couleurs.
  const setTheme = useCallback((next) => {
    const value = next === DARK ? DARK : LIGHT
    try {
      localStorage.setItem(STORAGE_KEY, value)
    } catch {
      // Sans stockage, le thème reste valable pour la session en cours.
    }
    const root = document.documentElement
    root.classList.add('theme-switching')
    window.setTimeout(() => root.classList.remove('theme-switching'), 250)
    setThemeState(value)
  }, [])

  const toggleTheme = useCallback(
    () => setTheme(theme === DARK ? LIGHT : DARK),
    [theme, setTheme]
  )

  // Thème par défaut de l'établissement (Module 11, §10), poussé par
  // SettingsContext au chargement de l'identité visuelle. Volontairement
  // non mémorisé : ce n'est qu'un point de départ, le choix personnel de
  // l'utilisateur (setTheme) reste prioritaire et n'est jamais écrasé. Un
  // défaut « système » n'appelle pas cette fonction : le comportement de base
  // suit déjà les préférences du système.
  const applyDefaultTheme = useCallback((preferred) => {
    if (preferred !== DARK && preferred !== LIGHT) return
    if (readStoredTheme() !== null) return
    setThemeState(preferred)
  }, [])

  const value = useMemo(
    () => ({ theme, isDark: theme === DARK, setTheme, toggleTheme, applyDefaultTheme }),
    [theme, setTheme, toggleTheme, applyDefaultTheme]
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

export function useTheme() {
  const context = useContext(ThemeContext)
  if (!context) throw new Error('useTheme doit être utilisé dans un ThemeProvider')
  return context
}
