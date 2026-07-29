import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { IconMenu, IconChevronDown, IconLogOut, IconLockReset } from '../icons'

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

  return (
    <header className="h-16 flex items-center justify-between px-4 lg:px-8 bg-white border-b border-ink/10">
      <button
        onClick={onMenuClick}
        className="lg:hidden text-ink/60 hover:text-ink"
        aria-label="Ouvrir le menu"
      >
        <IconMenu className="w-5 h-5" />
      </button>

      <div className="hidden lg:block" />

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
            <span className="block text-xs text-ink/50">{user?.role}</span>
          </span>
          <IconChevronDown className="w-4 h-4 text-ink/40" />
        </button>

        {menuOpen && (
          <>
            <div className="fixed inset-0 z-10" onClick={() => setMenuOpen(false)} />
            <div className="absolute right-0 mt-2 w-56 bg-white border border-ink/10 rounded-lg shadow-lg z-20 py-1">
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
                Deconnexion
              </button>
            </div>
          </>
        )}
      </div>
    </header>
  )
}