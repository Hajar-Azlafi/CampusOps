import { NavLink } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'
import { IconHome, IconUsers, IconX } from '../icons'

const navItems = [
  { to: '/dashboard', label: 'Tableau de bord', icon: IconHome, roles: null },
  { to: '/users', label: 'Utilisateurs', icon: IconUsers, roles: ['ADMIN'] },
]

export default function Sidebar({ mobileOpen, onClose }) {
  const { user } = useAuth()

  const visibleItems = navItems.filter(
    (item) => !item.roles || item.roles.includes(user?.role)
  )

  const content = (
    <div className="h-full flex flex-col bg-blueprint-800 text-white">
      <div className="px-6 py-6 border-b border-white/10">
        <p className="font-display text-xl font-semibold">
          Campus<span className="text-signal-light">Ops</span>
        </p>
        <p className="font-mono text-[10px] tracking-[0.15em] text-blueprint-line/60 uppercase mt-1">
          Gestion des espaces
        </p>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1">
        {visibleItems.map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onClose}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-signal/15 text-signal-light border-l-2 border-signal'
                  : 'text-blueprint-line/70 hover:bg-white/5 hover:text-white border-l-2 border-transparent'
              }`
            }
          >
            <Icon className="w-4 h-4 shrink-0" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="px-6 py-4 border-t border-white/10">
        <p className="font-mono text-[10px] text-blueprint-line/40">v1.0 · Module Utilisateurs</p>
      </div>
    </div>
  )

  return (
    <>
      <aside className="hidden lg:block w-64 shrink-0">{content}</aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/40" onClick={onClose} />
          <aside className="absolute left-0 top-0 bottom-0 w-64">
            <button
              onClick={onClose}
              aria-label="Fermer le menu"
              className="absolute right-3 top-3 text-white/70 hover:text-white"
            >
              <IconX className="w-5 h-5" />
            </button>
            {content}
          </aside>
        </div>
      )}
    </>
  )
}