import { useTheme } from '../context/ThemeContext'
import { IconSun, IconMoon } from './icons'

/**
 * Bouton de bascule clair / sombre.
 *
 * L'icône affichée est celle du thème que le clic activera (soleil en mode
 * sombre, lune en mode clair) : l'utilisateur voit l'action, pas l'état.
 *
 * `variant` :
 *   - "light" (défaut) : en-têtes clairs (Topbar admin, header enseignant/RP).
 *   - "dark"           : aplats bleu marine (panneau de connexion, sidebar),
 *                        sombres dans les deux thèmes -> teintes blanches.
 */
export default function ThemeToggle({ variant = 'light', className = '' }) {
  const { isDark, toggleTheme } = useTheme()

  const label = isDark ? 'Activer le mode clair' : 'Activer le mode sombre'

  const tone =
    variant === 'dark'
      ? 'text-white/70 hover:text-white hover:bg-white/10 focus-visible:outline-white/60'
      : 'text-ink/60 hover:text-ink hover:bg-ink/5 focus-visible:outline-blueprint-700'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={label}
      title={label}
      className={`inline-flex items-center justify-center w-9 h-9 rounded-lg transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 ${tone} ${className}`}
    >
      {isDark ? (
        <IconSun className="w-[18px] h-[18px]" aria-hidden="true" />
      ) : (
        <IconMoon className="w-[18px] h-[18px]" aria-hidden="true" />
      )}
    </button>
  )
}
