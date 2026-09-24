import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { homeRouteForRole } from '../constants/roles'

export default function NotFoundPage() {
  const { user } = useAuth()
  const homeRoute = homeRouteForRole(user?.role)

  return (
    <main className="min-h-screen bg-canvas flex items-center justify-center px-6">
      <section className="max-w-lg text-center">
        <p className="text-sm font-semibold uppercase tracking-[0.2em] text-accent">Erreur 404</p>
        <h1 className="mt-4 font-display text-4xl font-semibold text-ink">Page introuvable</h1>
        <p className="mt-3 text-ink/60">
          Cette adresse ne correspond à aucune page disponible.
        </p>
        <Link
          to={homeRoute}
          className="mt-8 inline-flex items-center rounded-lg bg-accent px-5 py-3 font-semibold text-white transition hover:opacity-90"
        >
          Retour à l’accueil
        </Link>
      </section>
    </main>
  )
}