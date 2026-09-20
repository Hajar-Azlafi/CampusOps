import { useEffect, useState } from 'react'
import { fetchAvailableReports, downloadReport } from '../api/dashboardApi'
import { useAcademicYear } from '../context/AcademicYearContext'
import { IconFileText, IconFilePdf, IconFileSpreadsheet } from '../components/icons'

export default function ReportsPage() {
  const { selectedYearId } = useAcademicYear()
  const [reports, setReports] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [pending, setPending] = useState(null) // `${code}:${format}`

  useEffect(() => {
    fetchAvailableReports()
      .then(setReports)
      .catch(() => setError('Impossible de charger la liste des rapports.'))
      .finally(() => setLoading(false))
  }, [])

  const handleDownload = async (code, format) => {
    const token = `${code}:${format}`
    setPending(token)
    setError(null)
    try {
      await downloadReport(code, format, selectedYearId)
    } catch {
      setError("Le téléchargement du rapport a échoué. Veuillez réessayer.")
    } finally {
      setPending((current) => (current === token ? null : current))
    }
  }

  return (
    <div>
      <h1 className="font-display text-2xl font-semibold text-ink">Rapports & exports</h1>
      <p className="text-sm text-ink/50 mt-1 mb-6">
        Générez et téléchargez les rapports d'activité au format PDF ou Excel.
      </p>

      {error ? (
        <div className="border border-red-200 bg-red-50 text-red-700 rounded-xl p-4 text-sm mb-6">
          {error}
        </div>
      ) : null}

      {loading ? (
        <p className="text-sm text-ink/40">Chargement des rapports...</p>
      ) : reports.length === 0 ? (
        <p className="text-sm text-ink/40">Aucun rapport disponible.</p>
      ) : (
        <div className="grid sm:grid-cols-2 gap-4">
          {reports.map((report) => (
            <div key={report.code} className="bg-surface border border-ink/10 rounded-xl p-5 flex flex-col">
              <div className="flex items-start gap-3 mb-3">
                <span className="w-9 h-9 rounded-lg bg-heading/10 text-heading flex items-center justify-center shrink-0">
                  <IconFileText className="w-5 h-5" />
                </span>
                <div className="min-w-0">
                  <h2 className="text-sm font-semibold text-ink">{report.titre}</h2>
                  <p className="text-xs text-ink/50 mt-0.5">{report.description}</p>
                </div>
              </div>
              <div className="flex gap-2 mt-auto pt-2">
                <button
                  type="button"
                  onClick={() => handleDownload(report.code, 'pdf')}
                  disabled={pending === `${report.code}:pdf`}
                  className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-red-700 border border-red-200 rounded-lg hover:bg-red-50 transition-colors disabled:opacity-50"
                >
                  <IconFilePdf className="w-4 h-4" />
                  {pending === `${report.code}:pdf` ? 'Génération...' : 'PDF'}
                </button>
                <button
                  type="button"
                  onClick={() => handleDownload(report.code, 'excel')}
                  disabled={pending === `${report.code}:excel`}
                  className="inline-flex items-center gap-2 px-3 py-2 text-sm font-medium text-emerald-700 border border-emerald-200 rounded-lg hover:bg-emerald-50 transition-colors disabled:opacity-50"
                >
                  <IconFileSpreadsheet className="w-4 h-4" />
                  {pending === `${report.code}:excel` ? 'Génération...' : 'Excel'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
