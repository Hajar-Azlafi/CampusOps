// Couleurs de l'établissement (Module 11, §10 et §17) appliquées au thème
// existant. Aucun second système de thème n'est introduit : on se contente de
// réécrire, sur <html>, les mêmes variables CSS que `src/index.css` — donc tous
// les utilitaires Tailwind (bg-signal, bg-blueprint-800, text-heading …) suivent
// sans qu'aucune page ne soit modifiée.
//
// Deux familles seulement sont pilotées, pour ne jamais casser le mode sombre :
//   - « chrome » (couleur principale)  : marine du menu, des boutons primaires
//     et des titres. Les titres ne sont repeints qu'en clair : en sombre, le
//     texte doit rester très clair, c'est le thème qui décide.
//   - « accent » (couleur secondaire)  : la couleur d'action (liens actifs,
//     anneaux de focus, pastilles).
// Les fonds et l'encre (--color-paper, --color-surface, --color-ink) ne sont
// jamais touchés : ils appartiennent au thème clair/sombre.

/** Valeurs livrées par défaut : tant qu'elles sont en place, on ne touche à rien. */
export const DEFAULT_PRIMARY = '#0B1D33'
export const DEFAULT_ACCENT = '#E3A008'

const CHROME_PROPS = [
  '--color-nav',
  '--color-nav-deep',
  '--color-blueprint-900',
  '--color-blueprint-800',
  '--color-blueprint-700',
  '--color-heading',
  '--color-heading-soft',
]

const ACCENT_PROPS = ['--color-signal', '--color-signal-light', '--color-signal-dark']

/** `#aabbcc` (6 chiffres hexadécimaux) — seule forme acceptée par le backend. */
export function isValidHexColor(value) {
  return typeof value === 'string' && /^#[0-9a-fA-F]{6}$/.test(value.trim())
}

function toRgb(hex) {
  const clean = hex.trim().slice(1)
  return [
    parseInt(clean.slice(0, 2), 16),
    parseInt(clean.slice(2, 4), 16),
    parseInt(clean.slice(4, 6), 16),
  ]
}

function toHex([r, g, b]) {
  const part = (n) => Math.max(0, Math.min(255, Math.round(n))).toString(16).padStart(2, '0')
  return `#${part(r)}${part(g)}${part(b)}`
}

/** Mélange une couleur vers le blanc (`ratio` > 0) : variante plus claire. */
function lighten(hex, ratio) {
  return toHex(toRgb(hex).map((c) => c + (255 - c) * ratio))
}

/** Mélange une couleur vers le noir : variante plus foncée. */
function darken(hex, ratio) {
  return toHex(toRgb(hex).map((c) => c * (1 - ratio)))
}

/**
 * Luminance relative (WCAG) : sert à choisir un texte lisible sur un aplat de
 * la couleur choisie dans les aperçus.
 */
export function isLightColor(hex) {
  if (!isValidHexColor(hex)) return false
  const [r, g, b] = toRgb(hex).map((c) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.45
}

/** Couleur de texte lisible sur un aplat donné (aperçus uniquement). */
export function readableInkOn(hex) {
  return isLightColor(hex) ? '#0B1D33' : '#FFFFFF'
}

/**
 * Applique — ou retire — les couleurs de l'établissement.
 *
 * `isDark` conditionne deux choses : les titres ne sont repeints qu'en clair, et
 * la variante « texte » de l'accent s'éclaircit en sombre, exactement comme le
 * fait `html.dark` dans la feuille de style.
 */
export function applyBrandColors({ principale, secondaire, isDark }) {
  const root = document.documentElement
  const set = (prop, value) => root.style.setProperty(prop, value)
  const clear = (props) => props.forEach((prop) => root.style.removeProperty(prop))

  const chrome = isValidHexColor(principale) ? principale.trim() : null
  if (!chrome || chrome.toUpperCase() === DEFAULT_PRIMARY) {
    clear(CHROME_PROPS)
  } else if (isDark) {
    // En sombre, on n'éclaire que les aplats cliquables : le menu et les titres
    // restent gouvernés par le thème pour préserver les contrastes audités.
    clear(CHROME_PROPS)
    set('--color-blueprint-800', lighten(chrome, 0.18))
    set('--color-blueprint-700', lighten(chrome, 0.28))
  } else {
    set('--color-nav', chrome)
    set('--color-nav-deep', darken(chrome, 0.45))
    set('--color-blueprint-900', darken(chrome, 0.45))
    set('--color-blueprint-800', chrome)
    set('--color-blueprint-700', lighten(chrome, 0.2))
    set('--color-heading', chrome)
    set('--color-heading-soft', lighten(chrome, 0.2))
  }

  const accent = isValidHexColor(secondaire) ? secondaire.trim() : null
  if (!accent || accent.toUpperCase() === DEFAULT_ACCENT) {
    clear(ACCENT_PROPS)
  } else {
    set('--color-signal', accent)
    set('--color-signal-light', lighten(accent, 0.3))
    set('--color-signal-dark', isDark ? lighten(accent, 0.3) : darken(accent, 0.35))
  }
}

/** Rend la main au thème : utile à la sortie d'un aperçu non enregistré. */
export function clearBrandColors() {
  const root = document.documentElement
  ;[...CHROME_PROPS, ...ACCENT_PROPS].forEach((prop) => root.style.removeProperty(prop))
}
