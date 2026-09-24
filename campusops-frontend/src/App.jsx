import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { AcademicYearProvider } from './context/AcademicYearContext'
import { SettingsProvider } from './context/SettingsContext'
import { ThemeProvider } from './context/ThemeContext'
import ProtectedRoute from './components/ProtectedRoute'
import MainLayout from './components/layout/MainLayout'
import { homeRouteForRole, SIMPLE_NAV_ROLES } from './constants/roles'
import LoginPage from './pages/LoginPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ResetPasswordPage from './pages/ResetPasswordPage'
import ChangePasswordPage from './pages/ChangePasswordPage'
import UsersPage from './pages/UsersPage'
import ImportUsersPage from './pages/ImportUsersPage'
import DashboardPage from './pages/DashboardPage'
import BuildingsPage from './pages/BuildingsPage'
import FloorsPage from './pages/FloorsPage'
import SpacesPage from './pages/SpacesPage'
import EquipmentsPage from './pages/EquipmentsPage'
import DepartmentsPage from './pages/DepartmentsPage'
import ProgramsPage from './pages/ProgramsPage'
import LevelsPage from './pages/LevelsPage'
import PromotionsPage from './pages/PromotionsPage'
import GroupsPage from './pages/GroupsPage'
import SemestersPage from './pages/SemestersPage'
import AcademicYearsPage from './pages/AcademicYearsPage'
import NonWorkingDaysPage from './pages/NonWorkingDaysPage'
import TimeSlotsPage from './pages/TimeSlotsPage'
import TypesSeancesPage from './pages/TypesSeancesPage'
import ModulesPage from './pages/ModulesPage'
import SchedulesPage from './pages/SchedulesPage'
import ImportSchedulesPage from './pages/ImportSchedulesPage'
import TimetablesPage from './pages/TimetablesPage'
import TimetableGridPage from './pages/TimetableGridPage'
import ImportTimetablePage from './pages/ImportTimetablePage'
import OccupationsPage from './pages/OccupationsPage'
import ImportOccupationsPage from './pages/ImportOccupationsPage'
import ReservationsPage from './pages/ReservationsPage'
import MyReservationsPage from './pages/MyReservationsPage'
import AvailabilityPage from './pages/AvailabilityPage'
import ReportsPage from './pages/ReportsPage'
import NotificationsPage from './pages/NotificationsPage'
import HistoryPage from './pages/HistoryPage'
import AuditPage from './pages/AuditPage'
// Espace Responsable pedagogique (Section 10) + gestion admin des responsables (Section 13).
import RpDashboardPage from './pages/RpDashboardPage'
import MesFilieresPage from './pages/MesFilieresPage'
import MesGroupesPage from './pages/MesGroupesPage'
import RpSallesPage from './pages/RpSallesPage'
import RpDemandesPage from './pages/RpDemandesPage'
import MesModulesPage from './pages/MesModulesPage'
import ResponsablesPage from './pages/ResponsablesPage'
// Parametres et configuration de l'universite (Module 11), reserve a l'ADMIN.
import SettingsPage from './pages/SettingsPage'
import NotFoundPage from './pages/NotFoundPage'

// Roles habilites a utiliser les modules d'administration / infrastructure /
// academique (memes roles que ceux affiches dans la Sidebar existante).
const BACK_OFFICE_ROLES = ['ADMIN', 'RESPONSABLE_PEDAGOGIQUE']

function Placeholder({ title }) {
  return <h1 className="font-display text-2xl font-semibold text-ink">{title}</h1>
}

// Redirige vers la page d'accueil adaptee au role de l'utilisateur connecte.
function HomeRedirect() {
  const { user, isAuthenticated, loading } = useAuth()
  if (loading) return null
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <Navigate to={homeRouteForRole(user?.role)} replace />
}

function RouteFallback() {
  const { isAuthenticated, loading } = useAuth()
  if (loading) return null
  if (!isAuthenticated) return <Navigate to="/login" replace />
  return <NotFoundPage />
}

// "/reservations" pointe vers deux experiences differentes selon le role :
// suivi personnel pour ENSEIGNANT/RESPONSABLE_CLUB, gestion administrative
// pour ADMIN/RESPONSABLE_PEDAGOGIQUE. La page ADMIN n'est pas modifiee.
function ReservationsRoute() {
  const { user } = useAuth()
  return SIMPLE_NAV_ROLES.includes(user?.role) ? <MyReservationsPage /> : <ReservationsPage />
}

function App() {
  return (
    // ThemeProvider est le plus externe : le thème s'applique dès la page de
    // connexion, avant même qu'un utilisateur soit authentifié.
    // SettingsProvider vient juste après, car il lit le thème courant pour
    // appliquer les couleurs de l'établissement, et son endpoint est public :
    // nom, logo et couleurs doivent être connus avant toute authentification.
    <ThemeProvider>
      <SettingsProvider>
      <AuthProvider>
        <AcademicYearProvider>
        <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomeRedirect />} />
          <Route path="/login" element={<LoginPage />} />
          {/* Parcours mot de passe oublie : publiques, aucun jeton requis. */}
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />

          <Route
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          >
            <Route
              path="/dashboard"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <DashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/users"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <UsersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/users/import"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <ImportUsersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/buildings"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <BuildingsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/floors"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <FloorsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/spaces"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <SpacesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/equipments"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <EquipmentsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/departments"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <DepartmentsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/programs"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <ProgramsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/levels"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <LevelsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/promotions"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <PromotionsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/groups"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <GroupsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/semesters"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <SemestersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/academic-years"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <AcademicYearsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/time-slots"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <TimeSlotsPage />
                </ProtectedRoute>
              }
            />
            {/* Calendrier des jours non ouvrables (§4-§5) : jours fériés, fêtes
                religieuses (dates prévisionnelles), vacances et fermetures
                exceptionnelles. Administrable, générique pour toute université. */}
            <Route
              path="/calendrier"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <NonWorkingDaysPage />
                </ProtectedRoute>
              }
            />
            {/* Référentiels pédagogiques (§7-§8, §20) : types de séance
                configurables et modules (filière + semestre). Administration. */}
            <Route
              path="/types-seances"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <TypesSeancesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/modules"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <ModulesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/schedules"
              element={
                <ProtectedRoute roles={BACK_OFFICE_ROLES}>
                  <SchedulesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/schedules/import"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <ImportSchedulesPage />
                </ProtectedRoute>
              }
            />
            {/* Module Emplois du temps (§17) : page centrale, assistant d'import
                deux phases et grille de consultation. Accessible aux rôles
                back-office ; l'isolation par filière est imposée côté backend
                (EmploiDuTempsService + AccessScopeService), §12. */}
            <Route
              path="/timetables"
              element={
                <ProtectedRoute roles={BACK_OFFICE_ROLES}>
                  <TimetablesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/timetables/import"
              element={
                <ProtectedRoute roles={BACK_OFFICE_ROLES}>
                  <ImportTimetablePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/timetables/:id"
              element={
                <ProtectedRoute roles={BACK_OFFICE_ROLES}>
                  <TimetableGridPage />
                </ProtectedRoute>
              }
            />
            {/* Occupation supplémentaire : occupation des espaces en dehors de
                l'emploi du temps régulier, en trois sous-parties portées par le
                paramètre `?onglet=` (examens | soutenances | autre). Les trois
                partagent le même moteur central de disponibilité côté backend,
                et l'isolation par filière y est imposée en service (§12).
                Les anciennes URL /examens redirigent vers l'onglet examens pour
                ne casser aucun lien existant (favori, notification, e-mail). */}
            <Route
              path="/occupations"
              element={
                <ProtectedRoute roles={BACK_OFFICE_ROLES}>
                  <OccupationsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/occupations/import"
              element={
                <ProtectedRoute roles={BACK_OFFICE_ROLES}>
                  <ImportOccupationsPage />
                </ProtectedRoute>
              }
            />
            <Route path="/examens" element={<Navigate to="/occupations?onglet=examens" replace />} />
            <Route
              path="/examens/import"
              element={<Navigate to="/occupations/import?onglet=examens" replace />}
            />
            <Route path="/reservations" element={<ReservationsRoute />} />
            <Route path="/availability" element={<AvailabilityPage />} />
            <Route
              path="/reports"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <ReportsPage />
                </ProtectedRoute>
              }
            />
            <Route path="/notifications" element={<NotificationsPage />} />
            <Route path="/history" element={<HistoryPage />} />
            <Route
              path="/audit"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <AuditPage />
                </ProtectedRoute>
              }
            />

            {/* --- Espace Responsable pedagogique (Sections 9-10) ---
                Le gating par role est une defense en profondeur cote client ;
                l'isolation reelle par filiere est imposee cote backend
                (AccessScopeService), meme en cas d'acces direct a l'API. */}
            <Route
              path="/rp"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <RpDashboardPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/rp/filieres"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <MesFilieresPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/rp/groupes"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <MesGroupesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/rp/salles"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <RpSallesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/rp/demandes"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <RpDemandesPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/rp/mes-demandes"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <MyReservationsPage />
                </ProtectedRoute>
              }
            />
            {/* "Mes modules" (§20) : gestion des modules des filières du RP,
                bornée à son périmètre côté backend. */}
            <Route
              path="/rp/modules"
              element={
                <ProtectedRoute roles={['RESPONSABLE_PEDAGOGIQUE']}>
                  <MesModulesPage />
                </ProtectedRoute>
              }
            />

            {/* Gestion admin des responsables pedagogiques (Section 13). */}
            <Route
              path="/responsables"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <ResponsablesPage />
                </ProtectedRoute>
              }
            />

            {/* Parametres de l'universite (Module 11, §14). Le gating par role
                n'est qu'un confort de navigation : SettingsService verifie lui
                aussi que l'appelant est ADMIN sur chaque ecriture, donc un
                acces direct a /api/settings/** est refuse cote serveur. */}
            <Route
              path="/settings"
              element={
                <ProtectedRoute roles={['ADMIN']}>
                  <SettingsPage />
                </ProtectedRoute>
              }
            />

            <Route path="/change-password" element={<ChangePasswordPage />} />
          </Route>
          <Route path="*" element={<RouteFallback />} />
        </Routes>
        </BrowserRouter>
        </AcademicYearProvider>
      </AuthProvider>
      </SettingsProvider>
    </ThemeProvider>
  )
}

export default App