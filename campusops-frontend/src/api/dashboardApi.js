import axiosClient from './axiosClient'
import { downloadBlob } from '../utils/downloadBlob'

/**
 * Le tableau de bord distingue les statistiques annuelles (réservations,
 * occupation dérivée des séances) des comptages structurels globaux. Un
 * `academicYearId` optionnel cible une année de consultation ; à défaut, le
 * backend se cale sur l'année active.
 */
function yearParams(academicYearId) {
  const params = {}
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return params
}

/** Statistiques consolidees du tableau de bord (overview + espaces + reservations + temporel). */
export function fetchDashboardStatistics(academicYearId) {
  return axiosClient.get('/dashboard/statistics', { params: yearParams(academicYearId) }).then((res) => res.data)
}

export function fetchOverviewStats(academicYearId) {
  return axiosClient.get('/dashboard/overview', { params: yearParams(academicYearId) }).then((res) => res.data)
}

export function fetchSpaceStats(academicYearId) {
  return axiosClient.get('/dashboard/spaces', { params: yearParams(academicYearId) }).then((res) => res.data)
}

export function fetchReservationStats(academicYearId) {
  return axiosClient.get('/dashboard/reservations', { params: yearParams(academicYearId) }).then((res) => res.data)
}

export function fetchTemporalStats(academicYearId) {
  return axiosClient.get('/dashboard/temporal', { params: yearParams(academicYearId) }).then((res) => res.data)
}

/** Catalogue des rapports exportables. */
export function fetchAvailableReports() {
  return axiosClient.get('/dashboard/reports').then((res) => res.data)
}

/**
 * Telecharge un rapport au format demande (pdf ou excel) et declenche
 * l'enregistrement du fichier cote navigateur. `academicYearId` optionnel :
 * borne le rapport a l'annee de consultation (defaut = annee active).
 */
export function downloadReport(code, format = 'pdf', academicYearId) {
  const params = { format }
  if (academicYearId != null && academicYearId !== '') params.academicYearId = academicYearId
  return axiosClient
    .get(`/dashboard/reports/${code}/export`, {
      params,
      responseType: 'blob',
    })
    .then((res) => {
      const fileName = extractFileName(res) || defaultFileName(code, format)
      downloadBlob(res.data, fileName)
    })
}

function extractFileName(res) {
  const disposition = res.headers?.['content-disposition']
  if (!disposition) return null
  const match = /filename="?([^"]+)"?/.exec(disposition)
  return match ? match[1] : null
}

function defaultFileName(code, format) {
  const extension = format === 'excel' || format === 'xlsx' ? 'xlsx' : 'pdf'
  return `rapport-${code}.${extension}`
}
