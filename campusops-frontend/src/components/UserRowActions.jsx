import { useState, useRef, useEffect } from 'react'
import { createPortal } from 'react-dom'
import { ROLES } from '../constants/roles'

function IconDots(props) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" {...props}>
      <circle cx="5" cy="12" r="1.8" />
      <circle cx="12" cy="12" r="1.8" />
      <circle cx="19" cy="12" r="1.8" />
    </svg>
  )
}

export default function UserRowActions({ user, onEdit, onDeactivate, onReactivate, onResetPassword, onChangeRole }) {
  const [open, setOpen] = useState(false)
  const [roleSubmenu, setRoleSubmenu] = useState(false)
  const [coords, setCoords] = useState({ top: 0, left: 0 })
  const buttonRef = useRef(null)

  const close = () => { setOpen(false); setRoleSubmenu(false) }

  const updatePosition = () => {
    if (!buttonRef.current) return
    const rect = buttonRef.current.getBoundingClientRect()
    const menuWidth = 208
    let left = rect.right - menuWidth
    if (left < 8) left = 8
    setCoords({ top: rect.bottom + 4, left })
  }

  const toggleOpen = () => {
    if (!open) updatePosition()
    setOpen((v) => !v)
  }

  useEffect(() => {
    if (!open) return
    const handleReposition = () => updatePosition()
    window.addEventListener('scroll', handleReposition, true)
    window.addEventListener('resize', handleReposition)
    return () => {
      window.removeEventListener('scroll', handleReposition, true)
      window.removeEventListener('resize', handleReposition)
    }
  }, [open])

  return (
    <div className="inline-block">
      <button
        ref={buttonRef}
        onClick={toggleOpen}
        className="p-1.5 text-ink/40 hover:text-ink/70 hover:bg-ink/5 rounded-lg transition-colors"
        aria-label="Actions"
      >
        <IconDots className="w-4 h-4" />
      </button>

      {open && createPortal(
        <>
          <div className="fixed inset-0 z-40" onClick={close} />
          <div
            style={{ position: 'fixed', top: coords.top, left: coords.left, width: 208 }}
            className="bg-white border border-ink/10 rounded-lg shadow-lg z-50 py-1 text-sm"
          >
            <button onClick={() => { close(); onEdit() }} className="w-full text-left px-4 py-2 text-ink/70 hover:bg-ink/5">
              Modifier
            </button>

            <div className="relative">
              <button
                onClick={() => setRoleSubmenu((v) => !v)}
                className="w-full text-left px-4 py-2 text-ink/70 hover:bg-ink/5 flex items-center justify-between"
              >
                Changer le role
                <span className="text-ink/30">›</span>
              </button>
              {roleSubmenu && (
                <div className="pl-2">
                  {ROLES.filter((r) => r.value !== user.role).map((r) => (
                    <button
                      key={r.value}
                      onClick={() => { close(); onChangeRole(r.value) }}
                      className="w-full text-left px-4 py-2 text-ink/60 hover:bg-ink/5 text-xs"
                    >
                      {r.label}
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button onClick={() => { close(); onResetPassword() }} className="w-full text-left px-4 py-2 text-ink/70 hover:bg-ink/5">
              Reinitialiser le mot de passe
            </button>

            <div className="border-t border-ink/10 my-1" />

            {user.isActive ? (
              <button onClick={() => { close(); onDeactivate() }} className="w-full text-left px-4 py-2 text-red-600 hover:bg-red-50">
                Desactiver
              </button>
            ) : (
              <button onClick={() => { close(); onReactivate() }} className="w-full text-left px-4 py-2 text-emerald-600 hover:bg-emerald-50">
                Reactiver
              </button>
            )}
          </div>
        </>,
        document.body
      )}
    </div>
  )
}