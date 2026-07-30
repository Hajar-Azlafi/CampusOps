import { useState, useRef } from 'react'
import { Link } from 'react-router-dom'
import { downloadImportTemplate, importUsersFile } from '../api/usersApi'
import { downloadBlob, downloadCsvFromAccounts } from '../utils/downloadBlob'
import {
  IconUpload,
  IconFileSpreadsheet,
  IconDownload,
  IconCheckCircle,
  IconAlertTriangle,
} from '../components/icons'

export default function ImportUsersPage() {
  const [file, setFile] = useState(null)
  const [dragActive, setDragActive] = useState(false)
  const [isImporting, setIsImporting] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const inputRef = useRef(null)

  const handleFileSelect = (selected) => {
    if (!selected) return
    const isExcel =
      selected.name.endsWith('.xlsx') || selected.name.endsWith('.xls')
    if (!isExcel) {
      setError('Veuillez selectionner un fichier Excel (.xlsx)')
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
    const response = await downloadImportTemplate()
    downloadBlob(response.data, 'modele_import_utilisateurs.xlsx')
  }

  const handleImport = async () => {
    if (!file) return
    setIsImporting(true)
    setError('')
    try {
      const data = await importUsersFile(file)
      setResult(data)
    } catch {
      setError("L'import a echoue. Verifiez le format du fichier.")
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
    <div className="max-w-3xl">
      <div className="flex items-center gap-2 text-sm text-ink/50 mb-2">
        <Link to="/users" className="hover:text-ink/80">Utilisateurs</Link>
        <span>/</span>
        <span>Import Excel</span>
      </div>

      <h1 className="font-display text-2xl font-semibold text-ink mb-1">
        Importer des utilisateurs
      </h1>
      <p className="text-sm text-ink/60 mb-8">
        Ajoutez plusieurs comptes en une seule fois a partir d'un fichier Excel.
      </p>

      {!result && (
        <>
          <div className="bg-white border border-ink/10 rounded-xl p-5 mb-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <IconFileSpreadsheet className="w-8 h-8 text-blueprint-800/70 shrink-0" />
              <div>
                <p className="text-sm font-medium text-ink">Modele Excel</p>
                <p className="text-xs text-ink/50">
                  Telechargez le modele avec les colonnes attendues
                </p>
              </div>
            </div>
            <button
              onClick={handleDownloadTemplate}
              className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-blueprint-800 border border-blueprint-800/25 rounded-lg hover:bg-blueprint-800/5 transition-colors shrink-0"
            >
              <IconDownload className="w-4 h-4" />
              Telecharger
            </button>
          </div>

          <div
            onDragOver={(e) => { e.preventDefault(); setDragActive(true) }}
            onDragLeave={() => setDragActive(false)}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-xl p-10 text-center transition-colors ${
              dragActive ? 'border-signal bg-signal/5' : 'border-ink/15 bg-white'
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
                  Glissez-deposez votre fichier ici, ou
                </p>
                <button
                  onClick={() => inputRef.current?.click()}
                  className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
                >
                  Choisir un fichier
                </button>
                <p className="text-xs text-ink/40 mt-3">Format accepte : .xlsx</p>
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
            <div className="bg-white border border-ink/10 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-ink">{result.totalRows}</p>
              <p className="text-xs text-ink/50 mt-1">Lignes traitees</p>
            </div>
            <div className="bg-emerald-50 border border-emerald-200 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-emerald-700">{result.successCount}</p>
              <p className="text-xs text-emerald-700/70 mt-1">Comptes crees</p>
            </div>
            <div className="bg-red-50 border border-red-200 rounded-xl p-4">
              <p className="text-2xl font-display font-semibold text-red-700">{result.errorCount}</p>
              <p className="text-xs text-red-700/70 mt-1">Erreurs</p>
            </div>
          </div>

          {result.createdAccounts?.length > 0 && (
            <div className="bg-white border border-ink/10 rounded-xl overflow-hidden mb-6">
              <div className="flex items-center justify-between px-5 py-3 border-b border-ink/10 bg-ink/[0.02]">
                <div className="flex items-center gap-2">
                  <IconCheckCircle className="w-4 h-4 text-emerald-600" />
                  <p className="text-sm font-medium text-ink">Comptes crees avec succes</p>
                </div>
                <button
                  onClick={() => downloadCsvFromAccounts(result.createdAccounts)}
                  className="flex items-center gap-1.5 text-xs font-medium text-blueprint-800 hover:underline"
                >
                  <IconDownload className="w-3.5 h-3.5" />
                  Exporter en CSV
                </button>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-ink/40 border-b border-ink/5">
                    <th className="px-5 py-2 font-normal">Email</th>
                    <th className="px-5 py-2 font-normal">Mot de passe temporaire</th>
                  </tr>
                </thead>
                <tbody>
                  {result.createdAccounts.map((acc) => (
                    <tr key={acc.email} className="border-b border-ink/5 last:border-0">
                      <td className="px-5 py-2.5 text-ink/80">{acc.email}</td>
                      <td className="px-5 py-2.5 font-mono text-ink/70">{acc.temporaryPassword}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {result.errors?.length > 0 && (
            <div className="bg-white border border-ink/10 rounded-xl overflow-hidden mb-6">
              <div className="flex items-center gap-2 px-5 py-3 border-b border-ink/10 bg-ink/[0.02]">
                <IconAlertTriangle className="w-4 h-4 text-red-600" />
                <p className="text-sm font-medium text-ink">Lignes en erreur</p>
              </div>
              <table className="w-full text-sm">
                <thead>
                  <tr className="text-left text-ink/40 border-b border-ink/5">
                    <th className="px-5 py-2 font-normal w-20">Ligne</th>
                    <th className="px-5 py-2 font-normal">Erreur</th>
                  </tr>
                </thead>
                <tbody>
                  {result.errors.map((err) => (
                    <tr key={err.row} className="border-b border-ink/5 last:border-0">
                      <td className="px-5 py-2.5 text-ink/60 font-mono">{err.row}</td>
                      <td className="px-5 py-2.5 text-red-700">{err.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex gap-3">
            <button
              onClick={handleReset}
              className="px-4 py-2 text-sm font-medium text-ink/70 border border-ink/15 rounded-lg hover:bg-ink/5 transition-colors"
            >
              Importer un autre fichier
            </button>
            <Link
              to="/users"
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
            >
              Retour a la liste
            </Link>
          </div>
        </div>
      )}
    </div>
  )
}