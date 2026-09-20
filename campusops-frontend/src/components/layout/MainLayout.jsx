import { useAuth } from '../../context/AuthContext'
import { SIMPLE_NAV_ROLES } from '../../constants/roles'
import AdminLayout from './AdminLayout'
import UserLayout from './UserLayout'

// Aiguille vers l'interface d'administration (Sidebar + Topbar) ou vers
// l'interface simplifiee (Navbar + contenu + footer) selon le role connecte.
export default function MainLayout() {
  const { user } = useAuth()
  const isSimpleNav = SIMPLE_NAV_ROLES.includes(user?.role)

  return isSimpleNav ? <UserLayout /> : <AdminLayout />
}