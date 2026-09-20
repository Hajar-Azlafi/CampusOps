import { useState, useRef, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import {
  downloadScheduleTemplate,
  importSchedulesFile,
  fetchScheduleImportHistory,
} from '../api/scheduleImportApi'
import { downloadBlob } from '../utils/downloadBlob'
import {
  IconUpload,
  IconFileSpreadsheet,
  IconDownload,
  IconCheckCircle,
  IconAlertTriangle,
} from '../components/icons'

export default function ImportSchedulesPage() {
  const [file, setFile] = useState(null)
  const [dragActive, setDragActive] = useState(false)
  const [isImporting, setIsImporting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [history, setHistory] = useState([])
  const inputRef = useRef(null)

  const loadHistory = useCallback(async () => {
    try {
      const data = await fetchScheduleImportHistory()
      setHistory(data)
    } catch {
      // silencieux : l'historique reste vide
    }
  }, [])

  useEffect(() => {
    loadHistory()
  }, [loadHistory])

  const handleFileSelect = (selected) => {
    if (!selected) return
    const isExcel = selected.name.endsWith('.xlsx') || selected.name.endsWith('.xls')
    if (!isExcel) {
      setError('Veuillez sélectionner un fichier Excel (.xlsx)')
      return
    }
    setError('')
    setFile(selected)
    setResult(null)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragActive(false)
    handleFileSelect(e.dataTransfer.files?.[0])
  }

  const handleDownloadTemplate = async () => {
    const response = await downloadScheduleTemplate()
    downloadBlob(response.data, 'modele_import_emplois_du_temps.xlsx')
  }

  const handleImport = async () => {
    if (!file) return
    setIsImporting(true)
    setError('')
    try {
      const data = await importSchedulesFile(file)
      setResult(data)
      loadHistory()
    } catch {
      setError("L'import a échoué. Vérifiez le format du fichier.")
    } finally {
      setIsImporting(false)
    }
  }

  const handleReset = () => {
    setFile(null)
    setResult(null)
    setError('')
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="max-w-4xl">
      <div className="flex items-center gap-2 text-sm text-ink/50 mb-2">
        <Link to="/schedules" className="hover:text-ink/80">Emplois du temps</Link>
        <span>/</span>
        <span>Import Excel</span>
      </div>

      <h1 className="font-display text-2xl font-semibold text-ink mb-1">
        Importer un emploi du temps
      </h1>
      <p className="text-sm text-ink/60 mb-8">
        Importez plusieurs séances en une seule fois à partir d'un fichier Excel. Les entités manquantes
        (filières, niveaux, promotions, groupes, semestres, années, créneaux) sont créées automatiquement.
      </p>

      {/* PLACEHOLDER_IMPORT */}
      {!result && (
        <>
          <div className="bg-surface border border-ink/10 rounded-xl p-5 mb-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <IconFileSpreadsheet className="w-8 h-8 text-heading/70 shrink-0" />
              <div>
                <p className="text-sm font-medium text-ink">Modèle Excel</p>
                <p className="text-xs text-ink/50">
                  Téléchargez le modèle avec les colonnes attendues
                </p>
              </div>
            </div>
            <button
              onClick={handleDownloadTemplate}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-heading border border-heading/25 rounded-lg hover:bg-heading/5 transition-colors shrink-0"
            >
              <IconDownload className="w-4 h-4" />
              Télécharger
            </button>
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
                <p className="text-sm text-ink/60 mb-3">
                  Glissez-déposez votre fichier ici, ou
                </p>
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
                <p className="text-xs text-ink/40 mb-4">
                  {(file.size / 1024).toFixed(1)} Ko
                </p>
                <div className="flex justify-center gap-3">
                  <button
                    onClick={handleReset}
                    className="px-4 py-2 text-sm font-medium text-ink/60 hover:bg-ink/5 rounded-lg transition-colors"
                  >
                    Changer de fichier
                  </button>
                  <button
                    onClick={handleImport}
                    disabled={isImporting}
                    className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 rounded-lg transition-colors"
                  >
                    {isImporting ? 'Import en cours...' : 'Importer'}
                  </button>
                </div>
              </>
            )}
          </div>

          {error && (
            <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5 mt-4">
              {error}
            </p>
          )}
        </>
      )}

      {result && (
        <div>
          <div className="grid grid-cols-3 gap-4 mb-6">
            <div className="bg-surface border border-ink/10 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-ink">{result.totalRows}</p>
              <p className="text-xs text-ink/50 mt-1">Lignes traitées</p>
            </div>
            <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-emerald-700">{result.successCount}</p>
              <p className="text-xs text-emerald-700/70 mt-1">Séances créées</p>
            </div>
            <div className="bg-red-50 border border-red-200 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-red-700">{result.errorCount}</p>
              <p className="text-xs text-red-700/70 mt-1">Erreurs</p>
            </div>
          </div>

          {result.createdEntities?.length > 0 && (
            <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden mb-6">
              <div className="flex items-center gap-2 px-5 py-3 border-b border-ink/10 bg-ink/[0.02]">
                <IconCheckCircle className="w-4 h-4 text-emerald-600" />
                <p className="text-sm font-medium text-ink">Entités créées automatiquement</p>
              </div>
              <ul className="px-5 py-3 space-y-1">
                {result.createdEntities.map((entity, i) => (
                  <li key={i} className="text-sm text-ink/70">{entity}</li>
                ))}
              </ul>
            </div>
          )}

          {result.errors?.length > 0 && (
            <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden mb-6">
              <div className="flex items-center gap-2 px-5 py-3 border-b border-ink/10 bg-ink/[0.02]">
                <IconAlertTriangle className="w-4 h-4 text-red-600" />
                <p className="text-sm font-medium text-ink">Lignes en erreur</p>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-ink/40 border-b border-ink/5">
                    <th className="px-5 py-2 font-normal">Feuille</th>
                    <th className="px-5 py-2 font-normal w-20">Ligne</th>
                    <th className="px-5 py-2 font-normal">Erreur</th>
                  </tr>
                </thead>
                <tbody>
                  {result.errors.map((err, i) => (
                    <tr key={i} className="border-b border-ink/5 last:border-0">
                      <td className="px-5 py-2.5 text-ink/60">{err.sheet}</td>
                      <td className="px-5 py-2.5 text-ink/60 font-mono">{err.row}</td>
                      <td className="px-5 py-2.5 text-red-700">{err.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex gap-3 mb-10">
            <button
              onClick={handleReset}
              className="px-4 py-2 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
            >
              Importer un autre fichier
            </button>
            <Link
              to="/schedules"
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
            >
              Voir les emplois du temps
            </Link>
          </div>
        </div>
      )}

      {/* Historique des imports */}
      <div className="mt-10">
        <h2 className="font-display text-lg font-semibold text-ink mb-3">Historique des imports</h2>
        <div className="bg-surface border border-ink/10 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-ink/10 bg-ink/[0.02]">
                <th className="text-left px-4 py-3 font-medium text-ink/50">Fichier</th>
                <th className="text-left px-4 py-3 font-medium text-ink/50">Date</th>
                <th className="text-left px-4 py-3 font-medium text-ink/50">Importé par</th>
                <th className="text-left px-4 py-3 font-medium text-ink/50">Lignes</th>
                <th className="text-left px-4 py-3 font-medium text-ink/50">Succès</th>
                <th className="text-left px-4 py-3 font-medium text-ink/50">Erreurs</th>
              </tr>
            </thead>
            <tbody>
              {history.length === 0 && (
                <tr><td colSpan={6} className="text-center py-8 text-ink/40">Aucun import réalisé</td></tr>
              )}
              {history.map((h) => (
                <tr key={h.id} className="border-b border-ink/5 last:border-0 hover:bg-ink/[0.015]">
                  <td className="px-4 py-3 text-ink/80">{h.fileName}</td>
                  <td className="px-4 py-3 text-ink/60 whitespace-nowrap">{(h.importedAt || '').replace('T', ' ')}</td>
                  <td className="px-4 py-3 text-ink/70">{h.importedBy || '—'}</td>
                  <td className="px-4 py-3 text-ink/70">{h.totalRows}</td>
                  <td className="px-4 py-3 text-emerald-700">{h.successCount}</td>
                  <td className="px-4 py-3 text-red-700">{h.errorCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
