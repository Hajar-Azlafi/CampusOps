import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { roleLabel } from '../../constants/roles'
import { BrandLogo, BrandName } from '../Brand'
import NotificationBell from '../NotificationBell'
import ThemeToggle from '../ThemeToggle'
import {
  IconSearchLocation,
  IconBookmark,
  IconChevronDown,
  IconLogOut,
  IconLockReset,
  IconHistory,
  IconMenu,
  IconX,
} from '../icons'

function initials(firstName, lastName) {
  return `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase()
}

const navLinkClass = ({ isActive }) =>
  `relative flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
    isActive
      ? 'text-heading bg-heading/[0.06]'
      : 'text-ink/60 hover:text-heading hover:bg-ink/[0.04]'
  }`

/**
 * Interface dediee aux ENSEIGNANT / RESPONSABLE_CLUB : header clair (blanc) +
 * contenu clair + footer clair, sans sidebar d'administration. L'identite de
 * marque s'exprime par le logotype (bleu marine + accent signal) et un fin
 * liseré, pas par un aplat sombre. Objectif : plateforme institutionnelle,
 * sobre et legere visuellement.
 */
export default function UserLayout() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  // Libellé de rôle français canonique ; repli sur la valeur brute.
  const roleName = roleLabel(user?.role)

  return (
    <div className="min-h-screen flex flex-col bg-paper">
      <header className="bg-surface/95 backdrop-blur-sm border-b border-heading/10 shadow-[0_1px_2px_var(--shadow-header)] sticky top-0 z-30">
        <div className="max-w-5xl mx-auto px-4 lg:px-6 h-16 flex items-center justify-between gap-4">
          <div className="flex items-center gap-6">
            <NavLink to="/availability" className="flex items-center gap-2 shrink-0 min-w-0">
              {/* Logo de l'établissement s'il est configuré (§11) ; sinon la
                  pastille d'origine, pour ne rien changer par défaut. */}
              <BrandLogo
                className="w-8 h-8 rounded-lg object-contain"
                fallback={
                  <span className="w-8 h-8 rounded-lg bg-blueprint-800 flex items-center justify-center">
                    <IconSearchLocation className="w-4 h-4 text-signal-light" />
                  </span>
                }
              />
              <span className="font-display text-lg font-semibold text-heading truncate max-w-[9rem] sm:max-w-[15rem]">
                <BrandName court accentClass="text-signal" />
              </span>
            </NavLink>

            <nav className="hidden md:flex items-center gap-1">
              <NavLink to="/availability" className={navLinkClass}>
                <IconSearchLocation className="w-4 h-4" />
                Recherche d’espace
              </NavLink>
              <NavLink to="/reservations" className={navLinkClass}>
                <IconBookmark className="w-4 h-4" />
                Mes réservations
              </NavLink>
            </nav>
          </div>

          <div className="flex items-center gap-1.5">
            <ThemeToggle />

            <NotificationBell variant="light" />

            <div className="hidden sm:block w-px h-8 bg-heading/10 mx-1" />

            <div className="relative">
              <button
                onClick={() => setMenuOpen((v) => !v)}
                className="flex items-center gap-2.5 pl-1.5 pr-2 py-1.5 rounded-lg hover:bg-ink/[0.04] transition-colors"
              >
                <span className="w-9 h-9 rounded-full bg-blueprint-800 text-signal-light text-xs font-semibold flex items-center justify-center shrink-0">
                  {initials(user?.firstName, user?.lastName)}
                </span>
                <span className="hidden sm:block text-left leading-tight">
                  <span className="block text-sm font-medium text-ink">
                    {user?.firstName} {user?.lastName}
                  </span>
                  <span className="inline-flex items-center mt-0.5 px-1.5 py-px rounded text-[10px] font-medium bg-signal/10 text-signal-dark uppercase tracking-wide">
                    {roleLabel(user?.role)}
                  </span>
                </span>
                <IconChevronDown className="w-4 h-4 text-ink/40 hidden sm:block" />
              </button>

              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 mt-2 w-56 bg-surface border border-ink/10 rounded-lg shadow-lg z-20 py-1">
                    <div className="sm:hidden px-4 py-2.5 border-b border-ink/5">
                      <p className="text-sm font-medium text-ink">{user?.firstName} {user?.lastName}</p>
                      <p className="text-xs text-signal-dark">{roleName}</p>
                    </div>
                    <button
                      onClick={() => { setMenuOpen(false); navigate('/history') }}
                      className="w-full flex items-center gap-2 px-4 py-2.5 text-sm text-ink/70 hover:bg-ink/5 text-left"
                    >
                      <IconHistory className="w-4 h-4" />
                      Mon historique
                    </button>
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

            <button
              onClick={() => setMobileNavOpen((v) => !v)}
              className="md:hidden text-ink/60 hover:text-heading p-1"
              aria-label={mobileNavOpen ? 'Fermer le menu' : 'Ouvrir le menu'}
            >
              {mobileNavOpen ? <IconX className="w-5 h-5" /> : <IconMenu className="w-5 h-5" />}
            </button>
          </div>
        </div>

        {mobileNavOpen && (
          <nav className="md:hidden border-t border-heading/10 px-4 py-2 flex flex-col gap-1">
            <NavLink to="/availability" className={navLinkClass} onClick={() => setMobileNavOpen(false)}>
              <IconSearchLocation className="w-4 h-4" />
              Recherche d’espace
            </NavLink>
            <NavLink to="/reservations" className={navLinkClass} onClick={() => setMobileNavOpen(false)}>
              <IconBookmark className="w-4 h-4" />
              Mes réservations
            </NavLink>
          </nav>
        )}
      </header>

      <main className="flex-1">
        <div className="max-w-5xl mx-auto w-full px-4 lg:px-6 py-6 lg:py-8">
          <Outlet />
        </div>
      </main>

      <footer className="bg-surface border-t border-heading/10 mt-auto">
        <div className="max-w-5xl mx-auto px-4 lg:px-6 py-5 flex flex-col sm:flex-row items-center justify-between gap-2 text-xs text-ink/50">
          <span className="flex items-center gap-1.5">
            <span className="font-display font-semibold text-heading/70">Campus<span className="text-signal">Ops</span></span>
            <span className="text-ink/30">·</span>
            Gestion des espaces pédagogiques
          </span>
          <span className="text-ink/40">Trouvez rapidement une salle disponible et demandez sa réservation.</span>
        </div>
      </footer>
    </div>
  )
}