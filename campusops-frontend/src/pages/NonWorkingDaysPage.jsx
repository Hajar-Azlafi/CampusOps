import { useEffect, useState, useCallback } from 'react'
import {
  fetchNonWorkingDays,
  activateNonWorkingDay,
  deactivateNonWorkingDay,
  deleteNonWorkingDay,
} from '../api/nonWorkingDaysApi'
import { nonWorkingDayTypeLabel } from '../constants/nonWorkingDayTypes'
import { StatusBadge } from '../components/Badge'
import NonWorkingDayFormModal from '../components/NonWorkingDayFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import { useSettings } from '../context/SettingsContext'
import { IconCalendar, IconAlertTriangle } from '../components/icons'

// Administration du calendrier des jours non ouvrables (§4, §5).
//
// Le dimanche est un jour non ouvrable structurel gere directement par le moteur
// de disponibilite : il n'apparait donc pas ici. Cette page couvre les jours
// feries, fetes religieuses, vacances et fermetures exceptionnelles, entierement
// administrables pour n'importe quelle universite. Un jour peut etre desactive
// (il cesse alors de bloquer) plutot que supprime, afin d'en conserver l'trace.

const typeBadgeColors = {
  FERIE_NATIONAL: 'bg-red-50 text-red-700 border-red-200',
  FERIE_RELIGIEUX: 'bg-purple-50 text-purple-700 border-purple-200',
  VACANCES: 'bg-blue-50 text-blue-700 border-blue-200',
  FERMETURE_EXCEPTIONNELLE: 'bg-amber-50 text-amber-700 border-amber-200',
  PERSONNALISE: 'bg-ink/5 text-ink/60 border-ink/15',
}

function TypeBadge({ type, label }) {
  const cls = typeBadgeColors[type] ?? 'bg-ink/5 text-ink/60 border-ink/15'
  return (
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium border ${cls}`}>
      {label}
    </span>
  )
}

export default function NonWorkingDaysPage() {
  // Le motif de date n'est plus figé en dd/MM/yyyy : il vient des paramètres de
  // l'établissement (Paramètres > Affichage, §10). Les bornes restent saisies en
  // ISO dans le formulaire : seul l'affichage change.
  const { formatDate } = useSettings()
  const [days, setDays] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')

  const [actifFilter, setActifFilter] = useState('') // '', 'true', 'false'

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', day: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, day: null })
  const [actionLoading, setActionLoading] = useState(false)

  const loadDays = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      const data = await fetchNonWorkingDays({ actif: actifFilter })
      const list = Array.isArray(data) ? data : []
      // Tri chronologique pour une lecture calendaire.
      list.sort((a, b) => (a.dateDebut || '').localeCompare(b.dateDebut || ''))
      setDays(list)
    } catch {
      setErrorMsg('Impossible de charger le calendrier des jours non ouvrables')
    } finally {
      setLoading(false)
    }
  }, [actifFilter])

  useEffect(() => {
    loadDays()
  }, [loadDays])

  const openCreate = () => setFormModal({ open: true, mode: 'create', day: null })
  const openEdit = (day) => setFormModal({ open: true, mode: 'edit', day })
  const closeForm = () => setFormModal({ open: false, mode: 'create', day: null })

  const handleFormSuccess = () => {
    closeForm()
    loadDays()
  }

  const askActivate = (day) => setConfirmDialog({ open: true, type: 'activate', day })
  const askDeactivate = (day) => setConfirmDialog({ open: true, type: 'deactivate', day })
  const askDelete = (day) => setConfirmDialog({ open: true, type: 'delete', day })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, day: null })

  const handleConfirm = async () => {
    const { type, day } = confirmDialog
    setActionLoading(true)
    try {
      if (type === 'activate') {
        await activateNonWorkingDay(day.id)
      } else if (type === 'deactivate') {
        await deactivateNonWorkingDay(day.id)
      } else if (type === 'delete') {
        await deleteNonWorkingDay(day.id)
      }
      closeConfirm()
      loadDays()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'action a échoué, veuillez réessayer"
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const confirmContent = {
    activate: {
      title: 'Activer le jour',
      message: `Réactiver « ${confirmDialog.day?.libelle} » ? Il redeviendra un jour non ouvrable et bloquera les réservations.`,
      confirmLabel: 'Activer',
      danger: false,
    },
    deactivate: {
      title: 'Désactiver le jour',
      message: `Désactiver « ${confirmDialog.day?.libelle} » ? Il cessera de bloquer les réservations, sans être supprimé.`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    delete: {
      title: 'Supprimer le jour',
      message: `Supprimer définitivement « ${confirmDialog.day?.libelle} » ? Cette action est irréversible ; préférez la désactivation pour conserver l'historique.`,
      confirmLabel: 'Supprimer',
      danger: true,
    },
  }[confirmDialog.type] || {}

  const activeCount = days.filter((d) => d.actif).length

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Jours fériés & fermetures</h1>
          <p className="text-sm text-ink/50 mt-1">
            {days.length} jour(s) récurrent(s) ou exceptionnel(s) · {activeCount} actif(s)
          </p>
        </div>
        <button
          onClick={openCreate}
          className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
        >
          + Nouveau jour
        </button>
      </div>

      {/* Rappel : le dimanche est deja non ouvrable par defaut (§3). */}
      <div className="flex items-start gap-2.5 text-sm text-blue-800 bg-blue-50 border border-blue-200 rounded-xl px-4 py-3 mb-4">
        <IconCalendar className="w-4 h-4 mt-0.5 shrink-0" />
        <p>
          Le dimanche est un jour non ouvrable par défaut : inutile de l'ajouter ici. Les dates
          prévisionnelles (fêtes religieuses) restent ajustables à tout moment.
        </p>
      </div>

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4">
        <div className="flex flex-wrap items-end gap-4">
          <div>
            <label className="block text-xs font-medium text-ink/50 mb-1.5">Statut</label>
            <select
              value={actifFilter}
              onChange={(e) => setActifFilter(e.target.value)}
              className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="">Tous</option>
              <option value="true">Actifs</option>
              <option value="false">Inactifs</option>
            </select>
          </div>
        </div>
      </div>

      {errorMsg && (
        <p className="flex items-center gap-2 text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          <IconAlertTriangle className="w-4 h-4 shrink-0" />
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Dates</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Libellé</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Type</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Durée</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && days.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucun jour non ouvrable enregistré</td></tr>
            )}
            {!loading && days.map((day) => {
              const periode = day.dateFin && day.dateFin !== day.dateDebut
              return (
                <tr key={day.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                  <td className="px-4 py-3 font-mono text-ink/80 whitespace-nowrap">
                    {day.recurrent
                      ? new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: '2-digit' }).format(new Date(`${day.dateDebut}T00:00:00`))
                      : periode
                        ? `${formatDate(day.dateDebut)} → ${formatDate(day.dateFin)}`
                        : formatDate(day.dateDebut)}
                  </td>
                  <td className="px-4 py-3 font-medium text-ink">
                    {day.libelle}
                    {day.previsionnel && (
                      <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
                        prévisionnel
                      </span>
                    )}
                    {day.commentaire && (
                      <span className="block text-xs text-ink/45 font-normal mt-0.5">{day.commentaire}</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <TypeBadge type={day.type} label={day.typeLibelle || nonWorkingDayTypeLabel(day.type)} />
                  </td>
                  <td className="px-4 py-3 text-ink/70 whitespace-nowrap">
                    {day.nombreJours ? `${day.nombreJours} jour(s)` : '1 jour'}
                  </td>
                  <td className="px-4 py-3"><StatusBadge active={day.actif} /></td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button
                      onClick={() => openEdit(day)}
                      className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                    >
                      Modifier
                    </button>
                    {day.actif ? (
                      <button
                        onClick={() => askDeactivate(day)}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-amber-700 border border-amber-200 rounded-lg hover:bg-amber-50 transition-colors"
                      >
                        Désactiver
                      </button>
                    ) : (
                      <button
                        onClick={() => askActivate(day)}
                        className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                      >
                        Activer
                      </button>
                    )}
                    <button
                      onClick={() => askDelete(day)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Supprimer
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      <NonWorkingDayFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.day}
        onClose={closeForm}
        onSuccess={handleFormSuccess}
      />

      <ConfirmDialog
        open={confirmDialog.open}
        title={confirmContent.title}
        message={confirmContent.message}
        confirmLabel={confirmContent.confirmLabel}
        danger={confirmContent.danger}
        loading={actionLoading}
        onConfirm={handleConfirm}
        onCancel={closeConfirm}
      />
    </div>
  )
}
