import { useState } from 'react'
import { useNavigate, Link, NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { roleLabel } from '../../constants/roles'
import NotificationBell from '../NotificationBell'
import ThemeToggle from '../ThemeToggle'
import AcademicYearSelector from './AcademicYearSelector'
import {
  IconMenu,
  IconChevronDown,
  IconLogOut,
  IconLockReset,
  IconSearchLocation,
  IconSettings,
} from '../icons'

function initials(firstName, lastName) {
  return `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase()
}

export default function Topbar({ onMenuClick }) {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  // Libellé de rôle français canonique ; repli sur la valeur brute si la clé
  // n'existe pas (rôle inattendu), pour ne jamais afficher une valeur vide.
  const roleName = roleLabel(user?.role)

  return (
    <header className="h-16 flex items-center justify-between px-4 lg:px-8 bg-surface border-b border-ink/10">
      <button
        onClick={onMenuClick}
        className="lg:hidden text-ink/60 hover:text-ink"
        aria-label="Ouvrir le menu"
      >
        <IconMenu className="w-5 h-5" />
      </button>

      <div className="hidden lg:block" />

      <div className="flex items-center gap-2">
        <AcademicYearSelector />

        <Link
          to="/availability"
          className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
        >
          <IconSearchLocation className="w-4 h-4" />
          <span className="hidden sm:inline">Rechercher un espace</span>
        </Link>

        <ThemeToggle />

        <NotificationBell />

        {/* Raccourci vers les paramètres de l'établissement (§17), à côté de la
            cloche. Ce masquage est purement cosmétique : cette barre est
            partagée avec le Responsable pédagogique, et c'est le backend qui
            refuse toute lecture ou écriture non-ADMIN (§14). */}
        {user?.role === 'ADMIN' && (
          <NavLink
            to="/settings"
            aria-label="Paramètres"
            title="Paramètres"
            className={({ isActive }) =>
              `inline-flex items-center justify-center w-9 h-9 rounded-lg transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blueprint-700 ${
                isActive
                  ? 'text-heading bg-heading/[0.06]'
                  : 'text-ink/60 hover:text-ink hover:bg-ink/5'
              }`
            }
          >
            <IconSettings className="w-[18px] h-[18px]" aria-hidden="true" />
          </NavLink>
        )}

        <div className="relative">
        <button
          onClick={() => setMenuOpen((v) => !v)}
          className="flex items-center gap-3 px-2 py-1.5 rounded-lg hover:bg-ink/5 transition-colors"
        >
          <span className="w-8 h-8 rounded-full bg-blueprint-800 text-white text-xs font-semibold flex items-center justify-center">
            {initials(user?.firstName, user?.lastName)}
          </span>
          <span className="hidden sm:block text-left">
            <span className="block text-sm font-medium text-ink">
              {user?.firstName} {user?.lastName}
            </span>
            <span className="block text-xs text-ink/50">{roleName}</span>
          </span>
          <IconChevronDown className="w-4 h-4 text-ink/40" />
        </button>

        {menuOpen && (
          <>
            <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 mt-2 w-56 bg-surface border border-ink/10 rounded-lg shadow-lg z-20 py-1">
              <button
                onClick={() => { setMenuOpen(false); navigate('/change-password') }}
                className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-ink/70 hover:bg-ink/5 text-left"
              >
                <IconLockReset className="w-4 h-4" />
                Changer le mot de passe
              </button>
              <button
                onClick={handleLogout}
                className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 text-left"
              >
                <IconLogOut className="w-4 h-4" />
                Déconnexion
              </button>
            </div>
          </>
        )}
        </div>
      </div>
    </header>
  )
}