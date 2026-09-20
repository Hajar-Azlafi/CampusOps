import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  fetchNotifications,
  markNotificationAsRead,
  markAllNotificationsAsRead,
} from '../api/notificationsApi'
import { NOTIFICATION_TYPES, notificationTypeLabel } from '../constants/notificationTypes'
import { useAcademicYear } from '../context/AcademicYearContext'
import { useSettings } from '../context/SettingsContext'
import { usePagination } from '../hooks/usePagination'
import { NotificationTypeBadge } from '../components/Badge'
import Pagination from '../components/Pagination'

export default function NotificationsPage() {
  const navigate = useNavigate()
  const { selectedYearId } = useAcademicYear()
  // Formats de date/heure de l'établissement (Paramètres > Affichage, §10).
  const { formatDateHeure } = useSettings()
  const [notifications, setNotifications] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [typeFilter, setTypeFilter] = useState('')
  const [readFilter, setReadFilter] = useState('') // '' | 'unread' | 'read'

  const loadNotifications = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchNotifications(selectedYearId)
      setNotifications(data)
    } catch {
      setErrorMsg('Impossible de charger les notifications')
    } finally {
      setLoading(false)
    }
  }, [selectedYearId])

  useEffect(() => {
    loadNotifications()
  }, [loadNotifications])

  const filtered = useMemo(() => {
    let data = notifications
    if (typeFilter) data = data.filter((n) => n.type === typeFilter)
    if (readFilter === 'unread') data = data.filter((n) => !n.lue)
    if (readFilter === 'read') data = data.filter((n) => n.lue)
    return data
  }, [notifications, typeFilter, readFilter])

  const [page, setPage] = useState(1)
  const { pageAffichee, totalPages, pageItems, afficher } = usePagination(filtered, page)

  useEffect(() => {
    setPage(1)
  }, [typeFilter, readFilter])

  const unreadCount = useMemo(
    () => notifications.filter((n) => !n.lue).length,
    [notifications],
  )

  const handleMarkAsRead = async (notification) => {
    try {
      const updated = await markNotificationAsRead(notification.id)
      setNotifications((prev) => prev.map((n) => (n.id === updated.id ? updated : n)))
    } catch {
      setErrorMsg('Impossible de marquer la notification comme lue')
    }
  }

  const handleMarkAll = async () => {
    try {
      await markAllNotificationsAsRead()
      setNotifications((prev) => prev.map((n) => ({ ...n, lue: true })))
    } catch {
      setErrorMsg('Impossible de marquer les notifications comme lues')
    }
  }

  const handleOpenLink = async (notification) => {
    if (!notification.lue) {
      try {
        const updated = await markNotificationAsRead(notification.id)
        setNotifications((prev) => prev.map((n) => (n.id === updated.id ? updated : n)))
      } catch {
        // silencieux : la navigation reste possible
      }
    }
    navigate(notification.lien || '/notifications')
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Notifications</h1>
          <p className="text-sm text-ink/50 mt-1">
            {notifications.length} notification(s), dont {unreadCount} non lue(s)
          </p>
        </div>
        {unreadCount > 0 && (
          <button
            onClick={handleMarkAll}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            Tout marquer comme lu
          </button>
        )}
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
        <select
          value={readFilter}
          onChange={(e) => setReadFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Toutes</option>
          <option value="unread">Non lues</option>
          <option value="read">Lues</option>
        </select>

        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les types</option>
          {NOTIFICATION_TYPES.map((t) => (
            <option key={t.value} value={t.value}>{t.label}</option>
          ))}
        </select>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
        {loading && (
          <p className="text-sm text-ink/40 text-center py-10">Chargement...</p>
        )}
        {!loading && filtered.length === 0 && (
          <p className="text-sm text-ink/40 text-center py-10">Aucune notification trouvée</p>
        )}
        {!loading &&
          pageItems.map((n) => (
            <div
              key={n.id}
              className={`flex flex-col sm:flex-row sm:items-center gap-3 px-4 py-4 border-b border-ink/5 last:border-0 ${
                n.lue ? '' : 'bg-signal/[0.04]'
              }`}
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  {!n.lue && <span className="w-2 h-2 rounded-full bg-signal shrink-0" />}
                  <p className={`text-sm truncate ${n.lue ? 'text-ink/70' : 'font-medium text-ink'}`}>
                    {n.titre}
                  </p>
                  <NotificationTypeBadge type={n.type} label={notificationTypeLabel(n.type)} />
                </div>
                <p className="text-sm text-ink/60">{n.message}</p>
                <p className="font-mono text-[11px] text-ink/40 mt-1">{formatDateHeure(n.createdAt)}</p>
              </div>
              <div className="flex items-center gap-2 shrink-0">
                {n.lien && (
                  <button
                    onClick={() => handleOpenLink(n)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Ouvrir
                  </button>
                )}
                {!n.lue && (
                  <button
                    onClick={() => handleMarkAsRead(n)}
                    className="px-3 py-1.5 text-xs font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
                  >
                    Marquer comme lu
                  </button>
                )}
              </div>
            </div>
          ))}
      </div>

      {!loading && afficher && (
        <Pagination page={pageAffichee} totalPages={totalPages} onChange={setPage} />
      )}
    </div>
  )
}
