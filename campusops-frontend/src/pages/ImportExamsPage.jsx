import { useState, useRef, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchPrograms } from '../api/programsApi'
import { fetchPromotions } from '../api/promotionsApi'
import { fetchCurrentSemesters } from '../api/semestersApi'
import { fetchAcademicYears } from '../api/academicYearsApi'
import { fetchSessions } from '../api/academicSessionsApi'
import {
  downloadExamTemplate,
  previewExamImport,
  confirmExamImport,
} from '../api/examImportApi'
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

// Assistant d'import d'un PLANNING D'EXAMENS par un responsable pédagogique, en
// DEUX phases (même mécanique que l'import d'emploi du temps) :
//   1. Choix du contexte (année/filière/promotion/semestre/session)
//      + téléchargement du modèle + dépôt du fichier d'examens.
//   2. Prévisualisation détaillée (aucun enregistrement) puis confirmation.
// Différences avec l'import d'emploi du temps :
//   - PAS de niveau ni de groupe dans le contexte (le niveau est porté par la
//     promotion ; le groupe est facultatif et se saisit ligne par ligne).
//   - PAS de fenêtre de validité : chaque examen est daté ligne par ligne ; la
//     date est bornée au semestre côté backend.
// Le contexte n'est PAS dans le fichier : il est choisi ici et transmis au
// backend, qui vérifie l'accès à la filière (§12) et détecte les conflits.

const emptyContext = {
  academicYearId: '', programId: '', promotionId: '', semesterId: '', sessionId: '',
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

export default function ImportExamsPage() {
  const navigate = useNavigate()
  const inputRef = useRef(null)
  // Format de date de l'établissement (Paramètres > Affichage, §10) pour les
  // libellés de la page. L'aperçu du fichier reste en ISO : il doit refléter
  // exactement le contenu déposé.
  const { formatDate } = useSettings()

  const [step, setStep] = useState(1)
  const [refs, setRefs] = useState({
    programs: [], promotions: [], semesters: [], years: [], sessions: [],
  })
  const [context, setContext] = useState(emptyContext)
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
        const [programs, promotions, semesters, years, sessions] = await Promise.all([
          fetchPrograms({ actif: true }),
          fetchPromotions({ actif: true }),
          fetchCurrentSemesters(),
          fetchAcademicYears({ actif: true }),
          fetchSessions({ actif: true }),
        ])
        if (cancelled) return
        setRefs({ programs, promotions, semesters, years, sessions })
        const activeYear = years.find((y) => y.actif) || years[0]
        // Session par défaut : « Session normale » (le rattrapage reste choisi
        // explicitement via le sélecteur de session).
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

  // Promotions de la filière choisie (le niveau est porté par la promotion).
  const filteredPromotions = useMemo(() => {
    if (!context.programId) return []
    return refs.promotions.filter((p) => String(p.programId) === context.programId)
  }, [refs.promotions, context.programId])

  const selectedPromotion = useMemo(
    () => filteredPromotions.find((p) => String(p.id) === context.promotionId) || null,
    [filteredPromotions, context.promotionId],
  )

  // Semestres courants du niveau de la promotion sélectionnée. Replis non
  // cassants : promotion sans niveau ou aucun semestre du niveau → tous les
  // semestres courants.
  const filteredSemesters = useMemo(() => {
    const levelId = selectedPromotion?.levelId
    if (!levelId) return refs.semesters
    const byLevel = refs.semesters.filter((s) => String(s.levelId) === String(levelId))
    return byLevel.length ? byLevel : refs.semesters
  }, [refs.semesters, selectedPromotion])

  const selectedSemester = useMemo(
    () => refs.semesters.find((s) => String(s.id) === context.semesterId) || null,
    [refs.semesters, context.semesterId],
  )

  const setCtx = (field) => (e) => {
    const value = e.target.value
    setContext((c) => {
      const next = { ...c, [field]: value }
      if (field === 'programId') {
        next.promotionId = ''
        next.semesterId = ''
      } else if (field === 'promotionId') {
        next.semesterId = ''
      }
      return next
    })
  }

  // Auto-sélection du semestre dès qu'un seul est proposé ; purge si la
  // sélection courante n'est plus valide.
  useEffect(() => {
    if (filteredSemesters.length === 1) {
      const only = String(filteredSemesters[0].id)
      if (context.semesterId !== only) setContext((c) => ({ ...c, semesterId: only }))
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

  const numericContext = useMemo(() => ({
    academicYearId: Number(context.academicYearId),
    programId: Number(context.programId),
    promotionId: Number(context.promotionId),
    semesterId: Number(context.semesterId),
    sessionId: Number(context.sessionId),
  }), [context])

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
      const response = await downloadExamTemplate(numericContext)
      downloadBlob(response.data, 'modele_planning_examens.xlsx')
    } catch {
      setError('Le téléchargement du modèle a échoué.')
    } finally {
      setLoadingAction('')
    }
  }

  const handlePreview = async () => {
    if (!contextComplete || !file) return
    setLoadingAction('preview')
    setError('')
    try {
      const data = await previewExamImport(file, numericContext)
      setPreview(data)
      setStep(2)
    } catch (err) {
      setError(err.response?.data?.message || "L'analyse du fichier a échoué. Vérifiez le format.")
    } finally {
      setLoadingAction('')
    }
  }

  const handleConfirm = async () => {
    if (!file) return
    setLoadingAction('confirm')
    setError('')
    try {
      const data = await confirmExamImport(file, numericContext)
      setResult(data)
    } catch (err) {
      setError(err.response?.data?.message || "La confirmation a échoué. Aucun examen n'a été enregistré.")
    } finally {
      setLoadingAction('')
    }
  }

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
              {result.examensEnregistres} examen(s) enregistré(s).
            </p>
            {result.message && <p className="text-sm text-emerald-700/80 mt-1">{result.message}</p>}
          </div>
        </div>
        <div className="flex gap-3">
          <button
            onClick={() => navigate('/occupations?onglet=examens')}
            className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
          >
            Voir les examens
          </button>
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
        onClick={() => navigate('/occupations?onglet=examens')}
        className="inline-flex items-center gap-1.5 text-sm text-ink/60 hover:text-ink mb-4 transition-colors"
      >
        <IconArrowRight className="w-4 h-4 rotate-180" />
        Planning examens
      </button>

      <h1 className="font-display text-2xl font-semibold text-ink mb-1">Importer un planning d'examens</h1>
      <p className="text-sm text-ink/60 mb-6">
        Choisissez le contexte, téléchargez le modèle, puis déposez votre fichier d'examens.
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
            <h2 className="text-sm font-semibold text-ink mb-4">Contexte du planning d'examens</h2>
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
                <label className={labelClass}>Promotion</label>
                <select
                  value={context.promotionId}
                  onChange={setCtx('promotionId')}
                  disabled={!context.programId}
                  className={selectClass}
                >
                  <option value="">{!context.programId ? 'Choisir une filière...' : 'Sélectionner...'}</option>
                  {filteredPromotions.map((p) => <option key={p.id} value={p.id}>{p.nom}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass}>Semestre</label>
                <select
                  value={context.semesterId}
                  onChange={setCtx('semesterId')}
                  disabled={!context.promotionId}
                  className={selectClass}
                >
                  <option value="">{!context.promotionId ? 'Choisir une promotion...' : 'Sélectionner...'}</option>
                  {filteredSemesters.map((s) => <option key={s.id} value={s.id}>{s.nom}</option>)}
                </select>
                <p className="text-xs text-ink/45 mt-1">
                  {selectedSemester?.dateDebut && selectedSemester?.dateFin
                    ? `Les examens doivent tomber entre ${formatDate(selectedSemester.dateDebut)} et ${formatDate(selectedSemester.dateFin)}.`
                    : 'Seuls les semestres courants sont proposés.'}
                </p>
              </div>
              <div>
                <label className={labelClass}>Session</label>
                <select value={context.sessionId} onChange={setCtx('sessionId')} className={selectClass}>
                  <option value="">Sélectionner...</option>
                  {refs.sessions.map((s) => <option key={s.id} value={s.id}>{s.nom}</option>)}
                </select>
              </div>
            </div>

            <div className="flex items-center justify-between mt-5 pt-4 border-t border-ink/10">
              <p className="text-xs text-ink/50">
                Le modèle Excel est pré-rempli avec ce contexte et les valeurs autorisées.
                Le groupe se saisit ligne par ligne (vide = toute la promotion).
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
              disabled={!contextComplete || !file || loadingAction === 'preview'}
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
              <span><span className="text-ink/40">Semestre :</span> {preview.semestre}</span>
              <span><span className="text-ink/40">Session :</span> {preview.session}</span>
              <span><span className="text-ink/40">Année :</span> {preview.anneeUniversitaire}</span>
            </div>
          </div>

          {/* Compteurs */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
            <CounterCard value={preview.examensDetectes} label="Examens détectés" />
            <CounterCard value={preview.examensValides} label="Valides" tone="good" />
            <CounterCard value={preview.lignesEnErreur} label="Lignes en erreur" tone={preview.lignesEnErreur > 0 ? 'bad' : 'neutral'} />
            <CounterCard value={preview.conflits} label="Conflits" tone={preview.conflits > 0 ? 'warn' : 'neutral'} />
          </div>
          {(preview.sallesInexistantes > 0 || preview.sallesManquantes > 0 || preview.datesInvalides > 0 ||
            preview.creneauxInexistants > 0 || preview.modulesInexistants > 0 || preview.groupesInexistants > 0) && (
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-ink/60 mb-4">
              {preview.datesInvalides > 0 && <span>Dates invalides / hors semestre : {preview.datesInvalides}</span>}
              {preview.creneauxInexistants > 0 && <span>Créneaux non reconnus : {preview.creneauxInexistants}</span>}
              {preview.modulesInexistants > 0 && <span>Modules introuvables (contexte) : {preview.modulesInexistants}</span>}
              {preview.groupesInexistants > 0 && <span>Groupes introuvables : {preview.groupesInexistants}</span>}
              {preview.sallesInexistantes > 0 && <span>Salles introuvables : {preview.sallesInexistantes}</span>}
              {preview.sallesManquantes > 0 && <span>Salles manquantes : {preview.sallesManquantes}</span>}
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
                ? "Toutes les lignes sont valides. Vous pouvez confirmer l'import."
                : "Des lignes comportent des erreurs. Corrigez le fichier puis relancez l'analyse — aucun enregistrement ne sera fait tant que des erreurs subsistent."}
            </p>
          </div>

          {/* Détail ligne par ligne */}
          <div className="bg-surface border border-ink/10 rounded-xl overflow-x-auto mb-6">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-ink/10 bg-ink/[0.02] text-left text-ink/50">
                  <th className="px-3 py-2.5 font-medium w-12">#</th>
                  <th className="px-3 py-2.5 font-medium">Date</th>
                  <th className="px-3 py-2.5 font-medium">Horaire</th>
                  <th className="px-3 py-2.5 font-medium">Module</th>
                  <th className="px-3 py-2.5 font-medium">Salle</th>
                  <th className="px-3 py-2.5 font-medium">Groupe</th>
                  <th className="px-3 py-2.5 font-medium">État</th>
                </tr>
              </thead>
              <tbody>
                {(preview.lignes || []).map((row) => (
                  <tr key={row.ligne} className={`border-b border-ink/5 last:border-0 align-top ${row.valide ? '' : 'bg-red-50/40'}`}>
                    <td className="px-3 py-2.5 text-ink/40 font-mono">{row.ligne}</td>
                    <td className="px-3 py-2.5 text-ink/70 whitespace-nowrap">{row.date}</td>
                    <td className="px-3 py-2.5 text-ink/70 whitespace-nowrap">{row.heureDebut}–{row.heureFin}</td>
                    <td className="px-3 py-2.5 text-ink/80">{row.module}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.salle || '—'}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.groupe || 'Toute la promotion'}</td>
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
              {loadingAction === 'confirm' ? 'Enregistrement...' : `Confirmer l'import (${preview.examensValides})`}
            </button>
          </div>
        </>
      )}
    </div>
  )
}

