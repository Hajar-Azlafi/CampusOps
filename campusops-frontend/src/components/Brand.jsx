import { useSettings } from '../context/SettingsContext'

/**
 * Identité visuelle de l'établissement affichée hors de la page « Paramètres »
 * (§11) : menu latéral, page de connexion, barre supérieure.
 *
 * Deux règles gouvernent ces composants :
 *   - tant qu'aucun nom n'est configuré (repli « CampusOps »), le logotype
 *     d'origine en deux teintes est conservé : il fait partie du design ;
 *   - dès qu'un établissement a saisi son nom, il est affiché tel quel —
 *     découper « Université Hassan II » en deux couleurs serait arbitraire.
 *
 * Aucun appel réseau n'est fait ici : tout vient de SettingsContext, qui charge
 * une seule fois `GET /api/settings/branding` (endpoint public, indispensable
 * avant authentification).
 */

/** Initiales d'un nom d'établissement, pour les emplacements très étroits. */
function initiales(nom) {
  const lettres = (nom ?? '')
    .split(/[\s-]+/)
    .map((mot) => mot.charAt(0))
    .filter((lettre) => /\p{L}/u.test(lettre))
  return lettres.slice(0, 2).join('').toUpperCase()
}

/**
 * Nom de l'établissement. `accentClass` ne colore que la seconde moitié du
 * logotype de repli ; un nom configuré est rendu d'un seul tenant.
 * `court` privilégie le nom court, prévu pour les espaces restreints.
 */
export function BrandName({ accentClass = '', court = false }) {
  const { branding, nomAffiche } = useSettings()
  const nom = (court ? nomAffiche : branding.nom) ?? ''
  if (!nom || nom === 'CampusOps') {
    return (
      <>
        Campus<span className={accentClass}>Ops</span>
      </>
    )
  }
  return <>{nom}</>
}

/** Variante en initiales : menu latéral réduit, pastilles. */
export function BrandInitials({ accentClass = '' }) {
  const { nomAffiche } = useSettings()
  if (!nomAffiche || nomAffiche === 'CampusOps') {
    return (
      <>
        C<span className={accentClass}>O</span>
      </>
    )
  }
  return <>{initiales(nomAffiche)}</>
}

/**
 * Logo téléversé. Sans logo configuré, `fallback` est rendu à sa place : chaque
 * emplacement garde ainsi sa propre pastille par défaut.
 */
export function BrandLogo({ className, fallback = null }) {
  const { logoSrc, branding } = useSettings()
  if (!logoSrc) return fallback
  return (
    <img
      src={logoSrc}
      alt={`Logo ${branding.nom ?? ''}`.trim()}
      className={className}
    />
  )
}
