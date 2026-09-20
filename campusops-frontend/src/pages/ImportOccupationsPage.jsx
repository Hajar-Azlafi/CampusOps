import { useState, useRef, useEffect, useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { fetchPrograms } from '../api/programsApi'
import { fetchPromotions } from '../api/promotionsApi'
import {
  downloadOccupationTemplate,
  previewOccupationImport,
  confirmOccupationImport,
} from '../api/occupationImportApi'
import { downloadBlob } from '../utils/downloadBlob'
import { OCCUPATION_CATEGORIES, occupationTabBySlug } from '../constants/occupationTypes'
import ImportExamsPage from './ImportExamsPage'
import {
  IconUpload,
  IconFileSpreadsheet,
  IconDownload,
  IconCheckCircle,
  IconAlertTriangle,
  IconArrowRight,
} from '../components/icons'

// Assistant d'import d'un planning d'OCCUPATIONS SUPPLÉMENTAIRES — soutenances
// ou occupations « autre » — en DEUX phases, même mécanique que l'import
// d'examens et d'emploi du temps :
//   1. Contexte (filière/promotion) + téléchargement du modèle + dépôt du fichier ;
//   2. Prévisualisation détaillée (aucun enregistrement) puis confirmation.
//
// L'onglet d'origine est porté par `?onglet=` : c'est lui qui fixe la catégorie
// importée, jamais un champ de formulaire. L'onglet « examens » garde son propre
// assistant (contexte pédagogique complet) : cette page le monte tel quel, de
// sorte qu'une seule route `/occupations/import` serve les trois sous-parties.
//
// Le fichier ne contient que les événements datés ; le contexte est transmis en
// paramètres et revérifié côté serveur (périmètre §12, conflits via le moteur
// central de disponibilité).

const selectClass =
  'w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-surface focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal disabled:bg-ink/5 disabled:text-ink/40'
const labelClass = 'block text-sm font-medium text-ink/80 mb-1.5'

const CONFIG = {
  [OCCUPATION_CATEGORIES.SOUTENANCE]: {
    backLabel: 'Planning soutenances',
    title: 'Importer un planning de soutenances',
    intro: 'Choisissez la filière concernée, téléchargez le modèle, puis déposez votre planning. '
      + 'Vous pourrez vérifier chaque ligne avant tout enregistrement.',
    contextTitle: 'Contexte du planning de soutenances',
    templateFile: 'modele_planning_soutenances.xlsx',
    unit: 'soutenance(s)',
    detected: 'Soutenances détectées',
    seeLabel: 'Voir les soutenances',
    previewError: "L'analyse du fichier a échoué. Vérifiez le format.",
    confirmError: "La confirmation a échoué. Aucune soutenance n'a été enregistrée.",
    contextHint: 'Une soutenance doit être rattachée à une filière. La promotion est facultative, '
      + 'mais elle est nécessaire pour pouvoir renseigner la colonne « Groupe » du fichier.',
  },
  [OCCUPATION_CATEGORIES.AUTRE]: {
    backLabel: 'Autres occupations',
    title: 'Importer des occupations',
    intro: 'Renseignez le contexte si l’occupation concerne une filière, téléchargez le modèle, '
      + 'puis déposez votre fichier. Vous pourrez vérifier chaque ligne avant tout enregistrement.',
    contextTitle: 'Contexte des occupations',
    templateFile: 'modele_autres_occupations.xlsx',
    unit: 'occupation(s)',
    detected: 'Occupations détectées',
    seeLabel: 'Voir les occupations',
    previewError: "L'analyse du fichier a échoué. Vérifiez le format.",
    confirmError: "La confirmation a échoué. Aucune occupation n'a été enregistrée.",
    contextHint: 'Le rattachement à une filière est facultatif : sans filière, l’import relève de '
      + 'l’administrateur. La promotion est nécessaire pour renseigner la colonne « Groupe ».',
  },
}

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

// L'onglet fixe la catégorie importée. « examens » conserve son assistant
// dédié : mêmes deux phases, mais un contexte pédagogique complet.
export default function ImportOccupationsPage() {
  const [searchParams] = useSearchParams()
  const tab = occupationTabBySlug(searchParams.get('onglet'))
  if (tab.categorie === OCCUPATION_CATEGORIES.EXAMEN) return <ImportExamsPage />
  return <OccupationImportWizard key={tab.slug} slug={tab.slug} categorie={tab.categorie} />
}

function OccupationImportWizard({ slug, categorie }) {
  const navigate = useNavigate()
  const inputRef = useRef(null)
  const config = CONFIG[categorie]
  const programRequired = categorie === OCCUPATION_CATEGORIES.SOUTENANCE

  const [step, setStep] = useState(1)
  const [refs, setRefs] = useState({ programs: [], promotions: [] })
  const [context, setContext] = useState({ programId: '', promotionId: '' })
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
        const [programs, promotions] = await Promise.all([
          fetchPrograms({ actif: true }),
          fetchPromotions({ actif: true }),
        ])
        if (cancelled) return
        setRefs({ programs: programs || [], promotions: promotions || [] })
      } catch {
        if (!cancelled) setError('Impossible de charger les données de référence.')
      }
    })()
    return () => { cancelled = true }
  }, [])

  // Promotions de la filière choisie. Sans filière (cas « autre »), toute
  // promotion reste proposable : le serveur en déduira alors la filière.
  const filteredPromotions = useMemo(() => {
    if (!context.programId) return refs.promotions
    return refs.promotions.filter((p) => String(p.programId) === context.programId)
  }, [refs.promotions, context.programId])

  const setCtx = (field) => (e) => {
    const value = e.target.value
    setContext((c) => {
      const next = { ...c, [field]: value }
      // Changer de filière invalide la promotion qui lui était rattachée.
      if (field === 'programId') next.promotionId = ''
      return next
    })
    setError('')
  }

  // Contexte transmis au serveur : la catégorie vient de l'onglet, jamais d'un
  // champ. Les identifiants vides sont ignorés par le client d'API.
  const importContext = useMemo(
    () => ({ categorie, programId: context.programId, promotionId: context.promotionId }),
    [categorie, context.programId, context.promotionId],
  )

  const contextComplete = !programRequired || Boolean(context.programId)

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
      const response = await downloadOccupationTemplate(importContext)
      downloadBlob(response.data, config.templateFile)
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
      const data = await previewOccupationImport(file, importContext)
      setPreview(data)
      setStep(2)
    } catch (err) {
      setError(err.response?.data?.message || config.previewError)
    } finally {
      setLoadingAction('')
    }
  }

  const handleConfirm = async () => {
    if (!file) return
    setLoadingAction('confirm')
    setError('')
    try {
      const data = await confirmOccupationImport(file, importContext)
      setResult(data)
    } catch (err) {
      setError(err.response?.data?.message || config.confirmError)
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

  const retourListe = () => navigate(`/occupations?onglet=${slug}`)

  // ---------- Écran de résultat (après confirmation) ----------
  if (result) {
    return (
      <div className="max-w-3xl">
        <h1 className="font-display text-2xl font-semibold text-ink mb-6">Import terminé</h1>
        <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-6 mb-6 flex items-start gap-4">
          <IconCheckCircle className="w-8 h-8 text-emerald-600 shrink-0" />
          <div>
            <p className="font-medium text-emerald-800">
              {result.occupationsEnregistrees} {config.unit} enregistrée(s).
            </p>
            <p className="text-sm text-emerald-700/80 mt-1">
              {result.message
                || 'Les espaces concernés sont désormais occupés sur ces créneaux.'}
            </p>
          </div>
        </div>
        <div className="flex gap-3">
          <button
            onClick={retourListe}
            className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
          >
            {config.seeLabel}
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
        onClick={retourListe}
        className="inline-flex items-center gap-1.5 text-sm text-ink/60 hover:text-ink mb-4 transition-colors"
      >
        <IconArrowRight className="w-4 h-4 rotate-180" />
        {config.backLabel}
      </button>

      <h1 className="font-display text-2xl font-semibold text-ink mb-1">{config.title}</h1>
      <p className="text-sm text-ink/60 mb-6">{config.intro}</p>

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
            <h2 className="text-sm font-semibold text-ink mb-4">{config.contextTitle}</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className={labelClass}>
                  Filière {programRequired ? '' : '(facultative)'}
                </label>
                <select value={context.programId} onChange={setCtx('programId')} className={selectClass}>
                  <option value="">
                    {programRequired ? 'Sélectionner...' : 'Aucune filière'}
                  </option>
                  {refs.programs.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nom}{p.levelNom ? ` — ${p.levelNom}` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className={labelClass}>Promotion (facultative)</label>
                <select value={context.promotionId} onChange={setCtx('promotionId')} className={selectClass}>
                  <option value="">Aucune promotion</option>
                  {filteredPromotions.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.nom}{p.programNom ? ` — ${p.programNom}` : ''}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="flex items-center justify-between mt-5 pt-4 border-t border-ink/10">
              <p className="text-xs text-ink/50 pr-4">{config.contextHint}</p>
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
          {(!contextComplete || !file) && (
            <p className="text-xs text-ink/40 text-right mt-2">
              {!contextComplete
                ? 'Choisissez la filière puis sélectionnez un fichier pour continuer.'
                : 'Sélectionnez un fichier pour continuer.'}
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
              <span><span className="text-ink/40">Catégorie :</span> {preview.categorie}</span>
              <span><span className="text-ink/40">Filière :</span> {preview.filiere || 'Aucune'}</span>
              <span><span className="text-ink/40">Promotion :</span> {preview.promotion || 'Aucune'}</span>
            </div>
          </div>

          {/* Compteurs */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
            <CounterCard value={preview.occupationsDetectees} label={config.detected} />
            <CounterCard value={preview.occupationsValides} label="Valides" tone="good" />
            <CounterCard value={preview.lignesEnErreur} label="Lignes en erreur" tone={preview.lignesEnErreur > 0 ? 'bad' : 'neutral'} />
            <CounterCard value={preview.conflits} label="Conflits d'occupation" tone={preview.conflits > 0 ? 'warn' : 'neutral'} />
          </div>

          {(preview.datesInvalides > 0 || preview.heuresInvalides > 0 || preview.typesInvalides > 0
            || preview.groupesInexistants > 0 || preview.sallesInexistantes > 0
            || preview.sallesManquantes > 0) && (
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-xs text-ink/60 mb-4">
              {preview.datesInvalides > 0 && <span>Dates invalides / non ouvrables : {preview.datesInvalides}</span>}
              {preview.heuresInvalides > 0 && <span>Horaires invalides : {preview.heuresInvalides}</span>}
              {preview.typesInvalides > 0 && <span>Types non reconnus : {preview.typesInvalides}</span>}
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
                  <th className="px-3 py-2.5 font-medium">Type</th>
                  <th className="px-3 py-2.5 font-medium">Intitulé</th>
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
                    <td className="px-3 py-2.5 text-ink/70">{row.type || '—'}</td>
                    <td className="px-3 py-2.5 text-ink/80">{row.intitule || '—'}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.salle || '—'}</td>
                    <td className="px-3 py-2.5 text-ink/70">{row.groupe || 'Toute l’audience'}</td>
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
              {loadingAction === 'confirm' ? 'Enregistrement...' : `Confirmer l'import (${preview.occupationsValides})`}
            </button>
          </div>
        </>
      )}
    </div>
  )
}
