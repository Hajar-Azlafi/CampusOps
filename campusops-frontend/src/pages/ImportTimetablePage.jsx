import { useState, useRef, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchPrograms } from '../api/programsApi'
import { fetchLevels } from '../api/levelsApi'
import { fetchPromotions } from '../api/promotionsApi'
import { fetchGroups } from '../api/groupsApi'
import { fetchCurrentSemesters } from '../api/semestersApi'
import { fetchAcademicYears } from '../api/academicYearsApi'
import { fetchSessions } from '../api/academicSessionsApi'
import {
  downloadTimetableTemplate,
  previewTimetableImport,
  confirmTimetableImport,
} from '../api/timetableImportApi'
import { downloadBlob } from '../utils/downloadBlob'
import { useSettings } from '../context/SettingsContext'
import {
  IconUpload,
  IconFileSpreadsheet,
  IconDownload,
  IconCheckCircle,
  IconAlertTriangle,
  IconArrowRight,
} from '../components/icons'

// Assistant d'import d'emploi du temps en DEUX phases (cahier des charges §5, §8, §24) :
//   1. Choix du contexte (année/filière/niveau/promotion/groupe/semestre)
//      + téléchargement du modèle + dépôt du fichier de séances.
//   2. Prévisualisation détaillée (aucun enregistrement) puis confirmation.
// Le contexte n'est PAS dans le fichier : il est choisi ici et transmis au backend,
// qui vérifie l'accès à la filière (§12) et détecte les conflits globaux (§33).

const emptyContext = {
  academicYearId: '', programId: '', levelId: '',
  promotionId: '', groupId: '', semesterId: '', sessionId: '',
}

const selectClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

function CounterCard({ value, label, tone = 'neutral' }) {
  const tones = {
    neutral: 'bg-surface border-ink/10 text-ink',
    good: 'bg-emerald-50 border-emerald-200 text-emerald-700',
    bad: 'bg-red-50 border-red-200 text-red-700',
    warn: 'bg-amber-50 border-amber-200 text-amber-700',
  }
  return (
    <div className={`border rounded-xl p-4 ${tones[tone]}`}>
      <p className="text-2xl font-display font-semibold">{value}</p>
      <p className="text-xs opacity-70 mt-1">{label}</p>
    </div>
  )
}

export default function ImportTimetablePage() {
  const navigate = useNavigate()
  const inputRef = useRef(null)
  // Format de date de l'établissement (Paramètres > Affichage, §10) pour les
  // libellés de la page. Les champs de saisie et l'aperçu du fichier restent en
  // ISO : ils doivent refléter exactement ce qui sera envoyé au backend.
  const { formatDate } = useSettings()

  const [step, setStep] = useState(1)
  const [refs, setRefs] = useState({
    programs: [], levels: [], promotions: [], groups: [], semesters: [], years: [], sessions: [],
  })
  const [context, setContext] = useState(emptyContext)
  // Fenêtre de validité de l'emploi du temps choisie par le RP (§20) : bornée au
  // semestre courant sélectionné. Séparée du contexte car facultative — le
  // backend retombe sur la période du semestre si elle n'est pas renseignée.
  const [validity, setValidity] = useState({ dateDebut: '', dateFin: '' })
  const [file, setFile] = useState(null)
  const [dragActive, setDragActive] = useState(false)

  const [preview, setPreview] = useState(null)
  const [result, setResult] = useState(null)
  const [loadingAction, setLoadingAction] = useState('') // 'template' | 'preview' | 'confirm'
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [programs, levels, promotions, groups, semesters, years, sessions] = await Promise.all([
          fetchPrograms({ actif: true }),
          fetchLevels({ actif: true }),
          fetchPromotions({ actif: true }),
          fetchGroups({ actif: true }),
          fetchCurrentSemesters(),
          fetchAcademicYears({ actif: true }),
          fetchSessions({ actif: true }),
        ])
        if (cancelled) return
        setRefs({ programs, levels, promotions, groups, semesters, years, sessions })
        // Pré-sélection de l'année active (une seule année active à la fois).
        const activeYear = years.find((y) => y.actif) || years[0]
        // Session fixée automatiquement : le module n'expose pas le choix de
        // session (le semestre porte déjà la période automne/printemps). On
        // retient « Session normale » ; le rattrapage reste géré via l'API/admin.
        const defaultSession =
          sessions.find((s) => s.code === 'NORMALE') ||
          [...sessions].sort((a, b) => (a.ordre ?? 0) - (b.ordre ?? 0))[0]
        setContext((c) => ({
          ...c,
          ...(activeYear ? { academicYearId: String(activeYear.id) } : {}),
          ...(defaultSession ? { sessionId: String(defaultSession.id) } : {}),
        }))
      } catch {
        if (!cancelled) setError('Impossible de charger les données de référence.')
      }
    })()
    return () => { cancelled = true }
  }, [])

  // Cascades (mêmes règles que la saisie manuelle d'une séance).
  const filteredPromotions = useMemo(() => {
    if (!context.programId || !context.levelId) return []
    return refs.promotions.filter(
      (p) => String(p.programId) === context.programId && String(p.levelId) === context.levelId,
    )
  }, [refs.promotions, context.programId, context.levelId])

  const filteredGroups = useMemo(() => {
    if (!context.promotionId) return []
    return refs.groups.filter((g) => String(g.promotionId) === context.promotionId)
  }, [refs.groups, context.promotionId])

  // Groupe sélectionné : porte l'année d'étude (anneeNiveau) au sein du cycle.
  const selectedGroup = useMemo(
    () => filteredGroups.find((g) => String(g.id) === context.groupId) || null,
    [filteredGroups, context.groupId],
  )

  // Semestres proposés. On part des semestres COURANTS du niveau, puis — si le
  // groupe porte une année d'étude (anneeNiveau) — on ne garde que le semestre
  // impair courant de CETTE année : ordre = 2*anneeNiveau − 1 (1A → S1, 2A → S3,
  // 3A → S5…). Ainsi un groupe de 1A du Tronc commun ne voit que S1, pas S3.
  // Replis non cassants : niveau non choisi → tous les semestres ; aucun semestre
  // du niveau → liste complète ; anneeNiveau absent/legacy ou aucun match sur
  // l'ordre attendu → tous les semestres courants du niveau.
  const filteredSemesters = useMemo(() => {
    if (!context.levelId) return refs.semesters
    const byLevel = refs.semesters.filter((s) => String(s.levelId) === context.levelId)
    if (!byLevel.length) return refs.semesters

    const annee = selectedGroup?.anneeNiveau
    if (!annee || annee < 1) return byLevel

    const expectedOrdre = 2 * annee - 1
    const forYear = byLevel.filter((s) => s.ordre === expectedOrdre)
    return forYear.length ? forYear : byLevel
  }, [refs.semesters, context.levelId, selectedGroup])

  const setCtx = (field) => (e) => {
    const value = e.target.value
    setContext((c) => {
      const next = { ...c, [field]: value }
      if (field === 'programId' || field === 'levelId') {
        next.promotionId = ''
        next.groupId = ''
        next.semesterId = ''
      } else if (field === 'promotionId') {
        next.groupId = ''
        next.semesterId = ''
      } else if (field === 'groupId') {
        // Le semestre dépend de l'année d'étude du groupe : on le réinitialise
        // pour laisser l'auto-sélection choisir le semestre courant adéquat.
        next.semesterId = ''
      }
      return next
    })
  }

  // Auto-sélection du semestre : dès qu'un seul semestre est proposé (cas normal
  // une fois le groupe choisi, grâce à son année d'étude), on le sélectionne
  // automatiquement. Si la sélection courante n'est plus dans la liste proposée,
  // on la vide pour éviter d'importer sur un semestre incohérent.
  useEffect(() => {
    if (filteredSemesters.length === 1) {
      const only = String(filteredSemesters[0].id)
      if (context.semesterId !== only) {
        setContext((c) => ({ ...c, semesterId: only }))
      }
    } else if (
      context.semesterId &&
      !filteredSemesters.some((s) => String(s.id) === context.semesterId)
    ) {
      setContext((c) => ({ ...c, semesterId: '' }))
    }
  }, [filteredSemesters, context.semesterId])

  const contextComplete = useMemo(
    () => Object.values(context).every((v) => v !== '' && v != null),
    [context],
  )

  // Semestre sélectionné : porte les bornes de validité [dateDebut, dateFin] qui
  // encadrent la fenêtre choisie pour l'emploi du temps (§20).
  const selectedSemester = useMemo(
    () => refs.semesters.find((s) => String(s.id) === context.semesterId) || null,
    [refs.semesters, context.semesterId],
  )

  // Pré-remplit la fenêtre de validité avec les bornes du semestre dès qu'il est
  // choisi (le RP peut ensuite resserrer l'expiration, jamais l'élargir au-delà).
  // Réinitialise si le semestre change ou disparaît.
  useEffect(() => {
    if (selectedSemester && (selectedSemester.dateDebut || selectedSemester.dateFin)) {
      setValidity({
        dateDebut: selectedSemester.dateDebut || '',
        dateFin: selectedSemester.dateFin || '',
      })
    } else {
      setValidity({ dateDebut: '', dateFin: '' })
    }
  }, [selectedSemester])

  // Validité de la fenêtre saisie : bornée au semestre, début ≤ fin. Non
  // bloquante si le semestre n'a pas de dates (import legacy).
  const validityError = useMemo(() => {
    const { dateDebut, dateFin } = validity
    if (dateDebut && dateFin && dateFin < dateDebut) {
      return "La date d'expiration ne peut pas précéder la date de début."
    }
    if (selectedSemester) {
      const min = selectedSemester.dateDebut
      const max = selectedSemester.dateFin
      if (min && dateDebut && dateDebut < min) {
        return `La date de début ne peut pas précéder le début du semestre (${min}).`
      }
      if (max && dateFin && dateFin > max) {
        return `La date d'expiration ne peut pas dépasser la fin du semestre (${max}).`
      }
    }
    return ''
  }, [validity, selectedSemester])

  const numericContext = useMemo(() => ({
    academicYearId: Number(context.academicYearId),
    programId: Number(context.programId),
    levelId: Number(context.levelId),
    promotionId: Number(context.promotionId),
    groupId: Number(context.groupId),
    semesterId: Number(context.semesterId),
    sessionId: Number(context.sessionId),
  }), [context])

  // Contexte enrichi de la fenêtre de validité (envoyée seulement si saisie).
  const importPayload = useMemo(() => {
    const payload = { ...numericContext }
    if (validity.dateDebut) payload.dateDebut = validity.dateDebut
    if (validity.dateFin) payload.dateFin = validity.dateFin
    return payload
  }, [numericContext, validity])

  const handleFileSelect = (selected) => {
    if (!selected) return
    const isExcel = selected.name.endsWith('.xlsx') || selected.name.endsWith('.xls')
    if (!isExcel) {
      setError('Veuillez sélectionner un fichier Excel (.xlsx)')
      return
    }
    setError('')
    setFile(selected)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragActive(false)
    handleFileSelect(e.dataTransfer.files?.[0])
  }

  const handleDownloadTemplate = async () => {
    if (!contextComplete) return
    setLoadingAction('template')
    setError('')
    try {
      const response = await downloadTimetableTemplate(numericContext)
      downloadBlob(response.data, 'modele_emploi_du_temps.xlsx')
    } catch {
      setError('Le téléchargement du modèle a échoué.')
    } finally {
      setLoadingAction('')
    }
  }

  const handlePreview = async () => {
    if (!contextComplete || !file || validityError) return
    setLoadingAction('preview')
    setError('')
    try {
      const data = await previewTimetableImport(file, importPayload)
      setPreview(data)
      setStep(2)
    } catch (err) {
      setError(err.response?.data?.message || "L'analyse du fichier a échoué. Vérifiez le format.")
    } finally {
      setLoadingAction('')
    }
  }

  const handleConfirm = async () => {
    if (!file || validityError) return
    setLoadingAction('confirm')
    setError('')
    try {
      const data = await confirmTimetableImport(file, importPayload)
      setResult(data)
    } catch (err) {
      setError(err.response?.data?.message || "La confirmation a échoué. Aucune séance n'a été enregistrée.")
    } finally {
      setLoadingAction('')
    }
  }

  // Retour à l'étape 1 pour corriger le fichier (le contexte est conservé).
  const backToContext = () => {
    setStep(1)
    setPreview(null)
    setFile(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  const startOver = () => {
    setStep(1)
    setPreview(null)
    setResult(null)
    setFile(null)
    setError('')
    if (inputRef.current) inputRef.current.value = ''
  }

  // ---------- Écran de résultat (après confirmation) ----------
  if (result) {
    return (
      <div className="max-w-3xl">
        <h1 className="font-display text-2xl font-semibold text-ink mb-6">Import terminé</h1>
        <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-6 mb-6 flex items-start gap-4">
          <IconCheckCircle className="w-8 h-8 text-emerald-600 shrink-0" />
          <div>
            <p className="font-medium text-emerald-800">
              {result.seancesEnregistrees} séance(s) enregistrée(s)
              {result.emploiDuTempsCree ? ' — nouvel emploi du temps créé.' : ' — emploi du temps complété.'}
            </p>
            {result.message && <p className="text-sm text-emerald-700/80 mt-1">{result.message}</p>}
          </div>
        </div>
        <div className="flex gap-3">
          {result.emploiDuTempsId && (
            <button
              onClick={() => navigate(`/timetables/${result.emploiDuTempsId}`)}
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
            >
              Voir l'emploi du temps
            </button>
          )}
          <button
            onClick={startOver}
            className="px-4 py-2 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
          >
            Importer un autre fichier
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-4xl">
      <button
        onClick={() => navigate('/timetables')}
        className="inline-flex items-center gap-1.5 text-sm text-ink/60 hover:text-ink mb-4 transition-colors"
      >
        <IconArrowRight className="w-4 h-4 rotate-180" />
        Emplois du temps
      </button>

      <h1 className="font-display text-2xl font-semibold text-ink mb-1">Importer un emploi du temps</h1>
      <p className="text-sm text-ink/60 mb-6">
        Choisissez le contexte, téléchargez le modèle, puis déposez votre fichier de séances.
        Vous pourrez vérifier le contenu avant tout enregistrement.
      </p>

      {/* Indicateur d'étape */}
      <div className="flex items-center gap-3 mb-6">
        {[{ n: 1, label: 'Contexte & fichier' }, { n: 2, label: 'Prévisualisation' }].map((s, i) => (
          <div key={s.n} className="flex items-center gap-3">
            <div className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium ${
              step === s.n ? 'bg-blueprint-800 text-white'
                : step > s.n ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                : 'bg-ink/5 text-ink/50'
            }`}>
              <span className="w-5 h-5 rounded-full bg-white/20 flex items-center justify-center text-xs">
                {step > s.n ? '✓' : s.n}
              </span>
              {s.label}
            </div>
            {i === 0 && <IconArrowRight className="w-4 h-4 text-ink/30" />}
          </div>
        ))}
      </div>

      {/* ---------- Étape 1 : contexte + fichier ---------- */}
      {step === 1 && (
        <>
          <div className="bg-surface border border-ink/10 rounded-xl p-5 mb-4">
            <h2 className="text-sm font-semibold text-ink mb-4">Contexte de l'emploi du temps</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              <div>
                <label className={labelClass}>Année universitaire</label>
                <select value={context.academicYearId} onChange={setCtx('academicYearId')} className={selectClass}>
                  <option value="">Sélectionner...</option>
                  {refs.years.map((y) => <option key={y.id} value={y.id}>{y.libelle}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass}>Filière</label>
                <select value={context.programId} onChange={setCtx('programId')} className={selectClass}>
                  <option value="">Sélectionner...</option>
                  {refs.programs.map((p) => <option key={p.id} value={p.id}>{p.nom}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass}>Niveau</label>
                <select value={context.levelId} onChange={setCtx('levelId')} className={selectClass}>
                  <option value="">Sélectionner...</option>
                  {refs.levels.map((l) => <option key={l.id} value={l.id}>{l.nom}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass}>Promotion</label>
                <select
                  value={context.promotionId}
                  onChange={setCtx('promotionId')}
                  disabled={!context.programId || !context.levelId}
                  className={selectClass}
                >
                  <option value="">
                    {!context.programId || !context.levelId ? 'Choisir filière + niveau...' : 'Sélectionner...'}
                  </option>
                  {filteredPromotions.map((p) => <option key={p.id} value={p.id}>{p.nom}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass}>Groupe</label>
                <select
                  value={context.groupId}
                  onChange={setCtx('groupId')}
                  disabled={!context.promotionId}
                  className={selectClass}
                >
                  <option value="">{!context.promotionId ? 'Choisir une promotion...' : 'Sélectionner...'}</option>
                  {filteredGroups.map((g) => <option key={g.id} value={g.id}>{g.nom}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass}>Semestre</label>
                <select
                  value={context.semesterId}
                  onChange={setCtx('semesterId')}
                  disabled={!context.groupId}
                  className={selectClass}
                >
                  <option value="">
                    {!context.groupId ? 'Choisir un groupe...' : 'Sélectionner...'}
                  </option>
                  {filteredSemesters.map((s) => <option key={s.id} value={s.id}>{s.nom}</option>)}
                </select>
                <p className="text-xs text-ink/45 mt-1">
                  {selectedGroup?.anneeNiveau
                    ? `Semestre courant de l'année ${selectedGroup.anneeNiveau} du groupe, sélectionné automatiquement.`
                    : 'Seuls les semestres courants sont proposés.'}
                </p>
              </div>
            </div>

            {/* Fenêtre de validité de l'emploi du temps (§20) : bornée au semestre. */}
            <div className="mt-5 pt-4 border-t border-ink/10">
              <h3 className="text-sm font-semibold text-ink mb-1">Période de validité de l'emploi du temps</h3>
              <p className="text-xs text-ink/50 mb-3">
                L'emploi du temps n'est valable qu'entre ces deux dates (au-delà, les salles sont
                libérées — notamment en période d'examens). La fenêtre doit rester dans le semestre
                {selectedSemester?.dateDebut && selectedSemester?.dateFin
                  ? ` courant (${formatDate(selectedSemester.dateDebut)} → ${formatDate(selectedSemester.dateFin)}).`
                  : ' courant sélectionné.'}
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className={labelClass}>Date de début</label>
                  <input
                    type="date"
                    value={validity.dateDebut}
                    min={selectedSemester?.dateDebut || undefined}
                    max={validity.dateFin || selectedSemester?.dateFin || undefined}
                    onChange={(e) => setValidity((v) => ({ ...v, dateDebut: e.target.value }))}
                    disabled={!context.semesterId}
                    className={selectClass}
                  />
                </div>
                <div>
                  <label className={labelClass}>Date d'expiration</label>
                  <input
                    type="date"
                    value={validity.dateFin}
                    min={validity.dateDebut || selectedSemester?.dateDebut || undefined}
                    max={selectedSemester?.dateFin || undefined}
                    onChange={(e) => setValidity((v) => ({ ...v, dateFin: e.target.value }))}
                    disabled={!context.semesterId}
                    className={selectClass}
                  />
                </div>
              </div>
              {validityError && (
                <p role="alert" className="text-xs text-red-700 mt-2">{validityError}</p>
              )}
            </div>

            <div className="flex items-center justify-between mt-5 pt-4 border-t border-ink/10">
              <p className="text-xs text-ink/50">
                Le modèle Excel est pré-rempli avec ce contexte et les valeurs autorisées.
              </p>
              <button
                onClick={handleDownloadTemplate}
                disabled={!contextComplete || loadingAction === 'template'}
                className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 disabled:opacity-50 disabled:cursor-not-allowed transition-colors shrink-0"
              >
                <IconDownload className="w-4 h-4" />
                {loadingAction === 'template' ? 'Préparation...' : 'Télécharger le modèle'}
              </button>
            </div>
          </div>

          <div
            onDragOver={(e) => { e.preventDefault(); setDragActive(true) }}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-xl p-10 text-center transition-colors ${
              dragActive ? 'border-signal bg-signal/5' : 'border-ink/15 bg-surface'
            }`}
          >
            <input
              ref={inputRef}
              type="file"
              accept=".xlsx,.xls"
              className="hidden"
              onChange={(e) => handleFileSelect(e.target.files?.[0])}
            />
            {!file ? (
              <>
                <IconUpload className="w-8 h-8 text-ink/30 mx-auto mb-3" />
                <p className="text-sm text-ink/60 mb-3">Glissez-déposez votre fichier ici, ou</p>
                <button
                  onClick={() => inputRef.current?.click()}
                  className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
                >
                  Choisir un fichier
                </button>
                <p className="text-xs text-ink/40 mt-3">Format accepté : .xlsx</p>
              </>
            ) : (
              <>
                <IconFileSpreadsheet className="w-8 h-8 text-emerald-600 mx-auto mb-3" />
                <p className="text-sm font-medium text-ink mb-1">{file.name}</p>
                <p className="text-xs text-ink/40 mb-4">{(file.size / 1024).toFixed(1)} Ko</p>
                <button
                  onClick={() => { setFile(null); if (inputRef.current) inputRef.current.value = '' }}
                  className="px-4 py-2 text-sm font-medium text-ink/60 hover:bg-ink/5 rounded-lg transition-colors"
                >
                  Changer de fichier
                </button>
              </>
            )}
          </div>

          {error && (
            <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mt-4">
              {error}
            </p>
          )}

          <div className="flex justify-end mt-6">
            <button
              onClick={handlePreview}
              disabled={!contextComplete || !file || !!validityError || loadingAction === 'preview'}
              className="flex items-center gap-2 px-5 py-2.5 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
            >
              {loadingAction === 'preview' ? 'Analyse en cours...' : 'Analyser le fichier'}
              {loadingAction !== 'preview' && <IconArrowRight className="w-4 h-4" />}
            </button>
          </div>
          {!contextComplete && (
            <p className="text-xs text-ink/40 text-right mt-2">
              Complétez le contexte et sélectionnez un fichier pour continuer.
            </p>
          )}
        </>
      )}

      {/* ---------- Étape 2 : prévisualisation ---------- */}
      {step === 2 && preview && (
        <>
          {/* Rappel du contexte */}
          <div className="bg-surface border border-ink/10 rounded-xl p-4 mb-4">
            <p className="text-xs text-ink/40 mb-2">Contexte d'import — {preview.fileName}</p>
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-ink/80">
              <span><span className="text-ink/40">Filière :</span> {preview.filiere}</span>
              <span><span className="text-ink/40">Niveau :</span> {preview.niveau}</span>
              <span><span className="text-ink/40">Promotion :</span> {preview.promotion}</span>
              <span><span className="text-ink/40">Groupe :</span> {preview.groupe}</span>
              <span><span className="text-ink/40">Semestre :</span> {preview.semestre}</span>
              <span><span className="text-ink/40">Année :</span> {preview.anneeUniversitaire}</span>
            </div>
          </div>

          {/* Compteurs */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
            <CounterCard value={preview.seancesDetectees} label="Séances détectées" />
            <CounterCard value={preview.seancesValides} label="Valides" tone="good" />
            <CounterCard value={preview.lignesEnErreur} label="Lignes en erreur" tone={preview.lignesEnErreur > 0 ? 'bad' : 'neutral'} />
            <CounterCard value={preview.conflits} label="Conflits" tone={preview.conflits > 0 ? 'warn' : 'neutral'} />
          </div>
          {(preview.sallesInexistantes > 0 || preview.sallesManquantes > 0 || preview.horairesInvalides > 0 ||
            preview.creneauxInexistants > 0 || preview.modulesInexistants > 0) && (
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-ink/60 mb-4">
              {preview.creneauxInexistants > 0 && <span>Créneaux non reconnus : {preview.creneauxInexistants}</span>}
              {preview.modulesInexistants > 0 && <span>Modules introuvables (contexte) : {preview.modulesInexistants}</span>}
              {preview.sallesInexistantes > 0 && <span>Salles introuvables : {preview.sallesInexistantes}</span>}
              {preview.sallesManquantes > 0 && <span>Salles manquantes (présentiel) : {preview.sallesManquantes}</span>}
              {preview.horairesInvalides > 0 && <span>Horaires invalides : {preview.horairesInvalides}</span>}
            </div>
          )}

          {/* Bandeau de verdict */}
          <div className={`flex items-start gap-3 rounded-xl p-4 mb-4 border ${
            preview.confirmable ? 'bg-emerald-50 border-emerald-200' : 'bg-amber-50 border-amber-200'
          }`}>
            {preview.confirmable
              ? <IconCheckCircle className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
              : <IconAlertTriangle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />}
            <p className={`text-sm ${preview.confirmable ? 'text-emerald-800' : 'text-amber-800'}`}>
              {preview.confirmable
                ? 'Toutes les lignes sont valides. Vous pouvez confirmer l\'import.'
                : 'Des lignes comportent des erreurs. Corrigez le fichier puis relancez l\'analyse — aucun enregistrement ne sera fait tant que des erreurs subsistent.'}
            </p>
          </div>

          {/* Détail ligne par ligne */}
          <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto mb-6">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-ink/10 bg-ink/[0.02] text-left text-ink/50">
                  <th className="px-3 py-2.5 font-medium w-12">#</th>
                  <th className="px-3 py-2.5 font-medium">Jour</th>
                  <th className="px-3 py-2.5 font-medium">Horaire</th>
                  <th className="px-3 py-2.5 font-medium">Module</th>
                  <th className="px-3 py-2.5 font-medium">Type</th>
                  <th className="px-3 py-2.5 font-medium">Présence</th>
                  <th className="px-3 py-2.5 font-medium">Salle</th>
                  <th className="px-3 py-2.5 font-medium">Enseignant</th>
                  <th className="px-3 py-2.5 font-medium">État</th>
                </tr>
              </thead>
              <tbody>
                {(preview.lignes || []).map((row) => (
                  <tr key={row.ligne} className={`border-b border-ink/5 last:border-0 align-top ${row.valide ? '' : 'bg-red-50/40'}`}>
                    <td className="px-3 py-2.5 text-ink/40 font-mono">{row.ligne}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.jour}</td>
                    <td className="px-3 py-2.5 text-ink/70 whitespace-nowrap">{row.heureDebut}–{row.heureFin}</td>
                    <td className="px-3 py-2.5 text-ink/80">{row.module}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.type}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.typePresence}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.salle || '—'}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.enseignant}</td>
                    <td className="px-3 py-2.5">
                      {row.valide ? (
                        <span className="inline-flex items-center gap-1 text-emerald-700 text-xs font-medium">
                          <IconCheckCircle className="w-3.5 h-3.5" /> Valide
                        </span>
                      ) : (
                        <div className="text-xs text-red-700">
                          {(row.erreurs || []).map((msg, i) => <div key={i}>• {msg}</div>)}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {error && (
            <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mb-4">
              {error}
            </p>
          )}

          <div className="flex justify-between mb-10">
            <button
              onClick={backToContext}
              className="px-4 py-2.5 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
            >
              {preview.confirmable ? 'Changer de fichier' : 'Corriger et réimporter'}
            </button>
            <button
              onClick={handleConfirm}
              disabled={!preview.confirmable || loadingAction === 'confirm'}
              className="px-5 py-2.5 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 disabled:cursor-not-allowed rounded-lg transition-colors"
            >
              {loadingAction === 'confirm' ? 'Enregistrement...' : `Confirmer l'import (${preview.seancesValides})`}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
