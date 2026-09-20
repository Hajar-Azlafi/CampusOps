import { useState, useRef, useEffect, useLayoutEffect } from 'react'
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

export default function UserRowActions({
  user,
  onEdit,
  onDeactivate,
  onReactivate,
  onResetPassword,
  onDelete,
  onChangeRole,
}) {
  const [open, setOpen] = useState(false)
  const [roleSubmenu, setRoleSubmenu] = useState(false)
  const [coords, setCoords] = useState({ top: 0, left: 0 })
  const buttonRef = useRef(null)
  const menuRef = useRef(null)

  const close = () => { setOpen(false); setRoleSubmenu(false) }

  const updatePosition = () => {
    if (!buttonRef.current) return
    const rect = buttonRef.current.getBoundingClientRect()
    const menuWidth = 208
    const viewportPadding = 8
    const menuHeight = menuRef.current?.getBoundingClientRect().height ?? 320
    const availableHeight = window.innerHeight - viewportPadding * 2
    const renderedHeight = Math.min(menuHeight, availableHeight)
    let left = rect.right - menuWidth
    if (left < viewportPadding) left = viewportPadding
    if (left + menuWidth > window.innerWidth - viewportPadding) {
      left = window.innerWidth - menuWidth - viewportPadding
    }

    const spaceBelow = window.innerHeight - rect.bottom - viewportPadding
    const top = spaceBelow >= renderedHeight + 4
      ? rect.bottom + 4
      : Math.max(viewportPadding, rect.top - renderedHeight - 4)

    setCoords({ top, left })
  }

  const toggleOpen = () => {
    setOpen((v) => !v)
  }

  useLayoutEffect(() => {
    if (!open) return
    updatePosition()
  }, [open, roleSubmenu])

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
            ref={menuRef}
            style={{ position: 'fixed', top: coords.top, left: coords.left, width: 208 }}
            className="bg-surface border border-ink/10 rounded-lg shadow-lg z-50 py-1 text-sm max-h-[calc(100vh-1rem)] overflow-y-auto"
          >
            <button onClick={() => { close(); onEdit() }} className="w-full text-left px-4 py-2 text-ink/70 hover:bg-ink/5">
              Modifier
            </button>

            <div className="relative">
              <button
                onClick={() => setRoleSubmenu((v) => !v)}
                className="w-full text-left px-4 py-2 text-ink/70 hover:bg-ink/5 flex items-center justify-between"
              >
                Changer le rôle
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
              Réinitialiser le mot de passe
            </button>

            <div className="border-t border-ink/10 my-1" />

            {user.isActive ? (
              <button onClick={() => { close(); onDeactivate() }} className="w-full text-left px-4 py-2 text-amber-600 hover:bg-amber-50">
                Désactiver
              </button>
            ) : (
              <button onClick={() => { close(); onReactivate() }} className="w-full text-left px-4 py-2 text-emerald-600 hover:bg-emerald-50">
                Réactiver
              </button>
            )}

            {onDelete && (
              <button onClick={() => { close(); onDelete() }} className="w-full text-left px-4 py-2 text-red-600 hover:bg-red-50">
                Supprimer définitivement
              </button>
            )}
          </div>
        </>,
        document.body
      )}
    </div>
  )
}
