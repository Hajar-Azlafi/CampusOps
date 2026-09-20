import { useEffect, useState, useCallback } from 'react'
import {
  fetchSemesters,
  searchSemesters,
  deactivateSemester,
  activateSemester,
  fetchCurrentSemesters,
  setSemesterCourant,
  unsetSemesterCourant,
  rolloverSemesters,
  deleteSemester,
  getSemesterDeletionImpact,
} from '../api/semestersApi'
import { fetchLevels } from '../api/levelsApi'
import { StatusBadge, CurrentBadge } from '../components/Badge'
import SemesterFormModal from '../components/SemesterFormModal'
import ConfirmDialog from '../components/ConfirmDialog'
import DeleteDialog from '../components/DeleteDialog'
import { useSettings } from '../context/SettingsContext'

export default function SemestersPage() {
  // Format de date de l'établissement (Paramètres > Affichage, §10). Les bornes
  // restent saisies en ISO dans le formulaire : seul l'affichage change.
  const { formatDate } = useSettings()
  const [semesters, setSemesters] = useState([])
  const [levels, setLevels] = useState([])
  const [currentSemesters, setCurrentSemesters] = useState([])
  const [loading, setLoading] = useState(true)
  const [errorMsg, setErrorMsg] = useState('')
  const [noticeMsg, setNoticeMsg] = useState('')

  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState('')
  const [levelFilter, setLevelFilter] = useState('')

  const [formModal, setFormModal] = useState({ open: false, mode: 'create', semester: null })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, type: null, semester: null })
  const [deleteTarget, setDeleteTarget] = useState(null)
  const [actionLoading, setActionLoading] = useState(false)

  const loadSemesters = useCallback(async () => {
    setLoading(true)
    setErrorMsg('')
    try {
      let data
      if (keyword.trim()) {
        data = await searchSemesters(keyword.trim())
        if (levelFilter) {
          data = data.filter((s) => String(s.levelId ?? '') === String(levelFilter))
        }
      } else {
        data = await fetchSemesters({
          actif: statusFilter === '' ? undefined : statusFilter === 'active',
          levelId: levelFilter || undefined,
        })
      }
      if (statusFilter && keyword.trim()) {
        data = data.filter((s) => s.actif === (statusFilter === 'active'))
      }
      setSemesters(data)
    } catch {
      setErrorMsg('Impossible de charger la liste des semestres')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [keyword, statusFilter, levelFilter])

  const loadCurrent = useCallback(async () => {
    try {
      setCurrentSemesters(await fetchCurrentSemesters())
    } catch {
      setCurrentSemesters([])
    }
  }, [])

  useEffect(() => {
    fetchLevels({ actif: true }).then(setLevels).catch(() => setLevels([]))
    loadSemesters()
    loadCurrent()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!keyword.trim()) loadSemesters()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, levelFilter])

  const handleSearchSubmit = (e) => {
    e.preventDefault()
    loadSemesters()
  }

  const openCreate = () => setFormModal({ open: true, mode: 'create', semester: null })
  const openEdit = (semester) => setFormModal({ open: true, mode: 'edit', semester })
  const closeForm = () => setFormModal({ open: false, mode: 'create', semester: null })

  const handleFormSuccess = () => {
    closeForm()
    loadSemesters()
  }

  const askDeactivate = (semester) => setConfirmDialog({ open: true, type: 'deactivate', semester })
  const askActivate = (semester) => setConfirmDialog({ open: true, type: 'activate', semester })
  const askDelete = (semester) => setDeleteTarget(semester)
  const askSetCourant = (semester) => setConfirmDialog({ open: true, type: 'setCourant', semester })
  const askUnsetCourant = (semester) => setConfirmDialog({ open: true, type: 'unsetCourant', semester })
  const askRollover = () => setConfirmDialog({ open: true, type: 'rollover', semester: null })
  const closeConfirm = () => setConfirmDialog({ open: false, type: null, semester: null })

  const handleConfirm = async () => {
    const { type, semester } = confirmDialog
    setActionLoading(true)
    setNoticeMsg('')
    try {
      if (type === 'deactivate') {
        await deactivateSemester(semester.id)
      } else if (type === 'activate') {
        await activateSemester(semester.id)
      } else if (type === 'setCourant') {
        await setSemesterCourant(semester.id)
        setNoticeMsg(`« ${semester.nom} » fait désormais partie des semestres courants de son niveau.`)
      } else if (type === 'unsetCourant') {
        await unsetSemesterCourant(semester.id)
        setNoticeMsg(`« ${semester.nom} » n'est plus un semestre courant.`)
      } else if (type === 'rollover') {
        const result = await rolloverSemesters()
        setNoticeMsg(result?.message || 'Bascule au semestre suivant effectuée.')
      }
      closeConfirm()
      loadSemesters()
      loadCurrent()
    } catch (err) {
      const msg = err?.response?.data?.message || "L'action a échoué, veuillez réessayer"
      setErrorMsg(msg)
      closeConfirm()
    } finally {
      setActionLoading(false)
    }
  }

  const hasCurrent = currentSemesters.length > 0
  const currentSummary = currentSemesters
    .map((s) => `${s.nom}${s.levelNom ? ` (${s.levelNom})` : ''}`)
    .join(', ')

  const confirmContent = {
    deactivate: {
      title: 'Désactiver le semestre',
      message: `Voulez-vous désactiver "${confirmDialog.semester?.nom}" ?`,
      confirmLabel: 'Désactiver',
      danger: true,
    },
    activate: {
      title: 'Activer le semestre',
      message: `Voulez-vous réactiver "${confirmDialog.semester?.nom}" ?`,
      confirmLabel: 'Activer',
      danger: false,
    },
    setCourant: {
      title: 'Ajouter aux semestres courants',
      message:
        `Marquer « ${confirmDialog.semester?.nom} » comme semestre courant de son niveau ? ` +
        `Un niveau peut avoir plusieurs semestres courants simultanés (cohortes coexistantes, ex. S1 + S3) : ` +
        `les autres courants du niveau ne sont pas retirés. Les formulaires de création de séances et d'examens ne proposent que les semestres courants.`,
      confirmLabel: 'Ajouter aux courants',
      danger: false,
    },
    unsetCourant: {
      title: 'Retirer des semestres courants',
      message:
        `Retirer « ${confirmDialog.semester?.nom} » des semestres courants de son niveau ? ` +
        `Il ne sera plus proposé lors de la création de séances ou d'examens. Aucune donnée n'est supprimée.`,
      confirmLabel: 'Retirer des courants',
      danger: false,
    },
    rollover: {
      title: 'Passer au semestre suivant',
      message: hasCurrent
        ? `Chaque niveau va avancer d'un semestre (S1→S2, S3→S4…). Semestre(s) courant(s) actuel(s) : ${currentSummary}. ` +
          `Les emplois du temps du semestre quitté (année active) seront archivés — leurs salles sont automatiquement libérées. ` +
          `Les niveaux déjà au dernier semestre restent inchangés. Aucune donnée n'est supprimée.`
        : `Aucun semestre courant n'est défini : utilisez d'abord « Définir courant » sur un semestre avant de basculer.`,
      confirmLabel: 'Passer au semestre suivant',
      danger: false,
    },
  }[confirmDialog.type] || {}

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="font-display text-2xl font-semibold text-ink">Semestres</h1>
          <p className="text-sm text-ink/50 mt-1">{semesters.length} semestre(s)</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button
            onClick={askRollover}
            disabled={!hasCurrent}
            title={hasCurrent ? undefined : 'Aucun semestre courant défini'}
            className="px-4 py-2.5 border border-heading/25 text-heading hover:bg-heading/5 disabled:opacity-40 disabled:cursor-not-allowed text-sm font-medium rounded-lg transition-colors"
          >
            Passer au semestre suivant
          </button>
          <button
            onClick={openCreate}
            className="px-4 py-2.5 bg-blueprint-800 hover:bg-blueprint-700 text-white text-sm font-medium rounded-lg transition-colors"
          >
            + Nouveau semestre
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
            placeholder="Rechercher par nom..."
            className="flex-1 px-3 py-2 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
          />
          <button type="submit" className="px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors">
            Rechercher
          </button>
        </form>

        <select
          value={levelFilter}
          onChange={(e) => setLevelFilter(e.target.value)}
          className="px-3 py-2 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
        >
          <option value="">Tous les niveaux</option>
          {levels.map((level) => (
            <option key={level.id} value={level.id}>{level.nom}</option>
          ))}
        </select>

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
              <th className="text-left px-4 py-3 font-medium text-ink/50">Ordre</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Nom</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Niveau / cycle</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Période</th>
              <th className="text-left px-4 py-3 font-medium text-ink/50">Statut</th>
              <th className="px-4 py-3" />
            </tr>
          </thead>
          <tbody>
            {loading && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Chargement...</td></tr>
            )}
            {!loading && semesters.length === 0 && (
              <tr><td colSpan={6} className="text-center py-10 text-ink/40">Aucun semestre trouvé</td></tr>
            )}
            {!loading && semesters.map((semester) => (
              <tr key={semester.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                <td className="px-4 py-3 text-ink/70">{semester.ordre}</td>
                <td className="px-4 py-3 font-medium text-ink">{semester.nom}</td>
                <td className="px-4 py-3 text-ink/70">
                  {semester.levelNom || <span className="text-ink/35">Libre</span>}
                </td>
                <td className="px-4 py-3 text-ink/60 whitespace-nowrap">
                  {semester.dateDebut || semester.dateFin ? (
                    `${semester.dateDebut ? formatDate(semester.dateDebut) : '—'} → ${semester.dateFin ? formatDate(semester.dateFin) : '—'}`
                  ) : (
                    <span className="text-ink/35">Année univ.</span>
                  )}
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-1.5">
                    <StatusBadge active={semester.actif} />
                    {semester.courant && <CurrentBadge />}
                  </div>
                </td>
                <td className="px-4 py-3 text-right whitespace-nowrap">
                  <button
                    onClick={() => openEdit(semester)}
                    className="px-3 py-1.5 text-xs font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors"
                  >
                    Modifier
                  </button>
                  {semester.actif && !semester.courant && (
                    <button
                      onClick={() => askSetCourant(semester)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-heading border border-signal/50 rounded-lg hover:bg-signal/10 transition-colors"
                    >
                      Ajouter courant
                    </button>
                  )}
                  {semester.actif && semester.courant && (
                    <button
                      onClick={() => askUnsetCourant(semester)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-ink/70 border border-ink/20 rounded-lg hover:bg-ink/5 transition-colors"
                    >
                      Retirer courant
                    </button>
                  )}
                  {semester.actif ? (
                    <button
                      onClick={() => askDeactivate(semester)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors"
                    >
                      Désactiver
                    </button>
                  ) : (
                    <button
                      onClick={() => askActivate(semester)}
                      className="ml-2 px-3 py-1.5 text-xs font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors"
                    >
                      Activer
                    </button>
                  )}
                  <button
                    onClick={() => askDelete(semester)}
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

      <SemesterFormModal
        open={formModal.open}
        mode={formModal.mode}
        initialData={formModal.semester}
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
        entityName="le semestre"
        impactFn={getSemesterDeletionImpact}
        deleteFn={deleteSemester}
        onClose={() => setDeleteTarget(null)}
        onDeleted={loadSemesters}
      />
    </div>
  )
}
