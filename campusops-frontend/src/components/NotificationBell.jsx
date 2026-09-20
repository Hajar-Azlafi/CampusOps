import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchUnreadNotifications,
  countUnreadNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from '../api/notificationsApi'
import { notificationTypeLabel } from '../constants/notificationTypes'
import { useSettings } from '../context/SettingsContext'
import { NotificationTypeBadge } from './Badge'
import { IconBell } from './icons'

const POLL_INTERVAL_MS = 30000
const PREVIEW_SIZE = 6

export default function NotificationBell({ variant = 'light' }) {
  const navigate = useNavigate()
  // Format date + heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDateHeure } = useSettings()
  const triggerClass =
    variant === 'dark'
      ? 'relative p-2 rounded-lg text-blueprint-line/70 hover:text-white hover:bg-white/10 transition-colors'
      : 'relative p-2 rounded-lg text-ink/60 hover:text-ink hover:bg-ink/5 transition-colors'
  const [open, setOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(false)

  const loadCount = useCallback(async () => {
    try {
      const data = await countUnreadNotifications()
      setUnreadCount(data.count ?? 0)
    } catch {
      // silencieux : le compteur n'est pas bloquant
    }
  }, [])

  const loadUnread = useCallback(async () => {
    setLoading(true)
    try {
      const data = await fetchUnreadNotifications()
      setItems(data.slice(0, PREVIEW_SIZE))
      setUnreadCount(data.length)
    } catch {
      setItems([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadCount()
    const timer = setInterval(loadCount, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [loadCount])

  useEffect(() => {
    if (open) loadUnread()
  }, [open, loadUnread])

  const handleItemClick = async (notification) => {
    try {
      await markNotificationAsRead(notification.id)
      setItems((prev) => prev.map((item) => (item.id === notification.id ? { ...item, lue: true } : item)))
      setUnreadCount((prev) => Math.max(prev - 1, 0))
    } catch {
      // silencieux : la navigation reste possible
    }
    setOpen(false)
    loadCount()
    navigate(notification.lien || '/notifications')
  }

  const handleMarkAll = async () => {
    try {
      await markAllNotificationsAsRead()
      setItems([])
      setUnreadCount(0)
    } catch {
      // silencieux
    }
  }

  return (
    <div className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        className={triggerClass}
        aria-label="Notifications"
      >
        <IconBell className="w-5 h-5" />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-signal text-white text-[10px] font-semibold flex items-center justify-center">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <>
          <div className="fixed inset-0 z-10" onClick={() => setOpen(false)} />
          <div className="absolute right-0 mt-2 w-80 sm:w-96 bg-surface border border-ink/10 rounded-lg shadow-lg z-20 overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 border-b border-ink/10">
              <p className="text-sm font-medium text-ink">Notifications</p>
              {unreadCount > 0 && (
                <button
                  onClick={handleMarkAll}
                  className="text-xs font-medium text-heading hover:underline"
                >
                  Tout marquer comme lu
                </button>
              )}
            </div>

            <div className="max-h-96 overflow-y-auto">
              {loading && (
                <p className="text-sm text-ink/40 text-center py-8">Chargement...</p>
              )}
              {!loading && items.length === 0 && (
                <p className="text-sm text-ink/40 text-center py-8">
                  Aucune notification non lue
                </p>
              )}
              {!loading &&
                items.map((n) => (
                  <button
                    key={n.id}
                    onClick={() => handleItemClick(n)}
                    className="w-full text-left px-4 py-3 border-b border-ink/5 last:border-0 hover:bg-ink/[0.02] transition-colors"
                  >
                    <div className="flex items-center justify-between gap-2 mb-1">
                      <p className="text-sm font-medium text-ink truncate">{n.titre}</p>
                      <NotificationTypeBadge type={n.type} label={notificationTypeLabel(n.type)} />
                    </div>
                    <p className="text-xs text-ink/60 line-clamp-2">{n.message}</p>
                    <p className="font-mono text-[10px] text-ink/40 mt-1">
                      {formatDateHeure(n.createdAt)}
                    </p>
                  </button>
                ))}
            </div>

            <button
              onClick={() => { setOpen(false); navigate('/notifications') }}
              className="w-full px-4 py-2.5 text-sm font-medium text-heading border-t border-ink/10 hover:bg-heading/5 transition-colors"
            >
              Voir toutes les notifications
            </button>
          </div>
        </>
      )}
    </div>
  )
}
