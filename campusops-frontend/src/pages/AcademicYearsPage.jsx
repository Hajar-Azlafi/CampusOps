import { useEffect, useState, useCallback } from 'react'
import {
  fetchAcademicYears,
  searchAcademicYears,
  deactivateAcademicYear,
  activateAcademicYear,
  rolloverAcademicYear,
  deleteAcademicYear,
  getAcademicYearDeletionImpact,
} from '../api/academicYearsApi'
import { StatusBadge } from '../components/Badge'
import AcademicYearFormModal from '../components/AcademicYearFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'
import { useSettings } from '../context/SettingsContext'

export default function AcademicYearsPage() {
  // Format de date de l'établissement (Paramètres > Affichage, §10). Les bornes
  // restent saisies en ISO dans le formulaire : seul l'affichage change.
  const { formatDate } = useSettings()
  const [years, setYears] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [noticeMsg, setNoticeMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', year: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, year: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadYears = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchAcademicYears(keyword.trim())
      } else {
        data = await fetchAcademicYears({ actif: statusFilter === '' ? undefined : statusFilter === 'active' })
      }
      if (statusFilter && keyword.trim()) {
        data = data.filter((y) => y.actif === (statusFilter === 'active'))
      }
      setYears(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des années universitaires')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter])

  useEffect(() => {
    loadYears()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadYears()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadYears()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', year: null })
  const openEdit = (year) => setFormModal({ open: true, mode: 'edit', year })
  const closeForm = () => setFormModal({ open: false, mode: 'create', year: null })

  const handleFormSuccess = () => {
    closeForm()
    loadYears()
  }

  const askDeactivate = (year) => setConfirmDialog({ open: true, type: 'deactivate', year })
  const askActivate = (year) => setConfirmDialog({ open: true, type: 'activate', year })
  const askDelete = (year) => setDeleteTarget(year)
  const askRollover = () => setConfirmDialog({ open: true, type: 'rollover', year: null })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, year: null })

  const handleConfirm = async () => {
    const { type, year } = confirmDialog
    setActionLoading(true)
    setNoticeMsg('')
    try {
      if (type === 'deactivate') {
        await deactivateAcademicYear(year.id)
      } else if (type === 'activate') {
        await activateAcademicYear(year.id)
      } else if (type === 'rollover') {
        const newYear = await rolloverAcademicYear()
        setNoticeMsg(
          `Passage effectué : « ${newYear.libelle} » est désormais l'année active. ` +
            `Les années précédentes et leurs données restent consultables.`,
        )
      }
      closeConfirm()
      loadYears()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'action a échoué, veuillez réessayer"
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const activeYear = years.find((y) => y.actif) || null
  const predictNextLabel = (libelle) => {
    const m = /(\d{4})\D+(\d{4})/.exec(libelle || '')
    return m ? `${Number(m[1]) + 1}-${Number(m[2]) + 1}` : null
  }
  const nextLabel = activeYear ? predictNextLabel(activeYear.libelle) : null

  const confirmContent = {
    deactivate: {
      title: "Désactiver l'année universitaire",
      message: `Voulez-vous désactiver "${confirmDialog.year?.libelle}" ? Ses données restent conservées et consultables.`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: "Activer l'année universitaire",
      message: `Voulez-vous rendre "${confirmDialog.year?.libelle}" active ? L'année active actuelle sera automatiquement désactivée (une seule année active à la fois). Aucune donnée n'est supprimée.`,
      confirmLabel: 'Activer',
      danger: false,
    },
    rollover: {
      title: "Passer à l'année suivante",
      message: activeYear
        ? `Passer de « ${activeYear.libelle} » à « ${nextLabel || 'l\'année suivante'} » ? ` +
          `La nouvelle année sera créée si besoin puis activée, et sa structure (promotions, groupes) initialisée. ` +
          `« ${activeYear.libelle} » sera désactivée mais entièrement conservée et consultable. Aucune donnée n'est supprimée.`
        : `Aucune année active n'est définie : activez d'abord une année avant de passer à la suivante.`,
      confirmLabel: 'Passer à l\'année suivante',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Années universitaires</h1>
          <p className="text-sm text-ink/50 mt-1">{years.length} année(s)</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={askRollover}
            disabled={!activeYear}
            title={activeYear ? undefined : "Aucune année active"}
            className="px-4 py-2.5 border border-heading/25 text-heading hover:bg-heading/5 disabled:opacity-40 disabled:cursor-not-allowed text-sm font-medium rounded-lg transition-colors"
          >
            Passer à l'année suivante
          </button>
          <button
            onClick={openCreate}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            + Nouvelle année
          </button>
        </div>
      </div>

      {noticeMsg && (
        <p className="text-sm text-emerald-800 bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2.5 mb-4">
          {noticeMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4 flex flex-col md:flex-row gap-3">
        <form onSubmit={handleSearchSubmit} className="flex-1 flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="Rechercher par libellé..."
            className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les statuts</option>
          <option value="active">Actifs</option>
          <option value="inactive">Inactifs</option>
        </select>
      </div>

      {errorMsg && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
          {errorMsg}
        </p>
      )}

      <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-ink/10 bg-ink/[0.02]">
              <th className="text-left px-4 py-3 font-medium text-ink/50">Année</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Début</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Fin</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && years.length === 0 && (
              <tr><td colSpan={5} className="text-center py-10 text-ink/40">Aucune année universitaire trouvée</td></tr>
            )}
            {!loading && years.map((year) => (
              <tr key={year.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 font-medium text-ink">{year.libelle}</td>
                <td className="px-4 py-3 text-ink/70">{year.dateDebut ? formatDate(year.dateDebut) : '—'}</td>
                <td className="px-4 py-3 text-ink/70">{year.dateFin ? formatDate(year.dateFin) : '—'}</td>
                <td className="px-4 py-3"><StatusBadge active={year.actif} /></td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(year)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {year.actif ? (
                    <button
                      onClick={() => askDeactivate(year)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(year)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(year)}
                    className="ml-2 px-3 py-1.5 text-xs font-medium text-red-700 border border-red-300 rounded-lg hover:bg-red-50 transition-colors"
                  >
                    Supprimer
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <AcademicYearFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.year}
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

      <DeleteDialog
        target={deleteTarget}
        entityName="l'année universitaire"
        impactFn={getAcademicYearDeletionImpact}
        deleteFn={deleteAcademicYear}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadYears}
      />
    </div>
  )
}
