import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { fetchAcademicYears, getCurrentAcademicYear } from '../api/academicYearsApi'
import { useAuth } from './AuthContext'

/**
 * Contexte de l'annee universitaire « de consultation ».
 *
 * L'application distingue les donnees globales (structurelles, constantes d'une
 * annee a l'autre) des donnees annuelles (reservations, emplois du temps,
 * examens, groupes, rapports, notifications, historique). Ce contexte porte
 * l'annee selectionnee pour la consultation et l'expose a toute l'application ;
 * les pages annuelles transmettent `selectedYearId` a leurs appels API plutot
 * que d'embarquer une logique `if annee == ...`.
 *
 * Points cles :
 * - l'annee selectionnee est initialisee sur l'annee ACTIVE (source de verite
 *   backend) ; l'utilisateur peut ensuite consulter une annee passee sans rien
 *   modifier ni supprimer (changement de contexte d'affichage uniquement) ;
 * - le choix est memorise (localStorage) pour survivre a un rechargement ;
 * - la selection ne modifie jamais l'annee active en base : activer une annee
 *   reste une action distincte de la page « Annees universitaires ».
 */
const AcademicYearContext = createContext(null)

const STORAGE_KEY = 'campusops.selectedAcademicYearId'

export function AcademicYearProvider({ children }) {
  const { isAuthenticated } = useAuth()
  const [years, setYears] = useState([])
  const [activeYear, setActiveYear] = useState(null)
  const [selectedYearId, setSelectedYearIdState] = useState(() => {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored ? Number(stored) : null
  })
  const [loading, setLoading] = useState(true)

  const setSelectedYearId = useCallback((id) => {
    const normalized = id == null || id === '' ? null : Number(id)
    setSelectedYearIdState(normalized)
    if (normalized == null) {
      localStorage.removeItem(STORAGE_KEY)
    } else {
      localStorage.setItem(STORAGE_KEY, String(normalized))
    }
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [all, current] = await Promise.all([
        fetchAcademicYears().catch(() => []),
        getCurrentAcademicYear().catch(() => null),
      ])
      setYears(Array.isArray(all) ? all : [])
      setActiveYear(current || null)
      // Sans selection memorisee (ou selection devenue invalide), on se cale
      // sur l'annee active : repli non cassant coherent avec le backend.
      setSelectedYearIdState((prev) => {
        const stillValid = prev != null && all?.some((y) => y.id === prev)
        if (stillValid) return prev
        const fallback = current?.id ?? null
        if (fallback == null) localStorage.removeItem(STORAGE_KEY)
        return fallback
      })
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      load()
    } else {
      setYears([])
      setActiveYear(null)
      setSelectedYearIdState(null)
      setLoading(false)
    }
  }, [isAuthenticated, load])

  const selectedYear = years.find((y) => y.id === selectedYearId) || activeYear || null
  const isViewingActiveYear =
    selectedYearId == null || (activeYear != null && selectedYearId === activeYear.id)

  const value = {
    years,
    activeYear,
    selectedYear,
    selectedYearId,
    setSelectedYearId,
    isViewingActiveYear,
    loading,
    reload: load,
  }

  return (
    <AcademicYearContext.Provider value={value}>{children}</AcademicYearContext.Provider>
  )
}

export function useAcademicYear() {
  const context = useContext(AcademicYearContext)
  if (!context) {
    throw new Error(
      "useAcademicYear doit etre utilise a l'interieur d'un AcademicYearProvider",
    )
  }
  return context
}
