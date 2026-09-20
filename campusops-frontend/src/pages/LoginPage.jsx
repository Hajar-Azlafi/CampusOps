import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { homeRouteForRole } from '../constants/roles'
import AuthShell from '../components/layout/AuthShell'

function IconMail(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" stroke="currentColor" {...props}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 7l9 6 9-6M4 5h16a1 1 0 011 1v12a1 1 0 01-1 1H4a1 1 0 01-1-1V6a1 1 0 011-1z" />
    </svg>
  )
}
function IconLock(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" stroke="currentColor" {...props}>
      <rect x="5" y="11" width="14" height="9" rx="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path strokeLinecap="round" strokeLinejoin="round" d="M8 11V8a4 4 0 018 0v3" />
    </svg>
  )
}
function IconEye(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" stroke="currentColor" {...props}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
function IconEyeOff(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="1.8" stroke="currentColor" {...props}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M3 3l18 18M10.6 10.6a3 3 0 004.24 4.24M9.9 5.1A10.7 10.7 0 0112 5.5c6 0 9.5 6.5 9.5 6.5a13.7 13.7 0 01-3.3 3.9M6.4 6.5C4 8.1 2.5 12 2.5 12s1.6 2.9 4.3 4.7" />
    </svg>
  )
}
function IconArrowRight(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" strokeWidth="2" stroke="currentColor" {...props}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  )
}

/**
 * Cle localStorage memorisant l'adresse du dernier utilisateur qui a coche
 * « Se souvenir de moi ». Elle ne contient jamais de mot de passe.
 */
const REMEMBERED_EMAIL_KEY = 'campusops.rememberedEmail'

export default function LoginPage() {
  // L'adresse memorisee est pre-remplie : c'est l'effet visible de la case
  // « Se souvenir de moi » lors du retour sur la page.
  const rememberedEmail = localStorage.getItem(REMEMBERED_EMAIL_KEY) || ''

  const [email, setEmail] = useState(rememberedEmail)
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(Boolean(rememberedEmail))
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    // Validation côté client avant tout appel réseau.
    const trimmedEmail = email.trim()
    if (!trimmedEmail || !password) {
      setError('Veuillez saisir votre adresse e-mail et votre mot de passe.')
      return
    }
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
    if (!emailPattern.test(trimmedEmail)) {
      setError('Veuillez saisir une adresse e-mail valide.')
      return
    }

    setIsSubmitting(true)
    try {
      const userData = await login(trimmedEmail, password, remember)

      // Deuxieme effet de la case : on retient (ou on oublie) l'adresse pour
      // la prochaine visite. Le mot de passe n'est jamais conserve.
      if (remember) {
        localStorage.setItem(REMEMBERED_EMAIL_KEY, trimmedEmail)
      } else {
        localStorage.removeItem(REMEMBERED_EMAIL_KEY)
      }

      navigate(userData.mustChangePassword ? '/change-password' : homeRouteForRole(userData.role))
    } catch (err) {
      const status = err.response?.status
      if (status === 401) {
        setError('Adresse e-mail ou mot de passe incorrect.')
      } else if (status === 403) {
        setError('Votre compte est désactivé. Contactez l\'administrateur.')
      } else if (!err.response) {
        // Aucune réponse du serveur (serveur arrêté, réseau, CORS...).
        setError('Impossible de se connecter au serveur. Veuillez réessayer plus tard.')
      } else if (status >= 500) {
        setError('Une erreur est survenue côté serveur. Veuillez réessayer plus tard.')
      } else {
        setError('La connexion a échoué. Veuillez réessayer.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <h2 className="font-display text-3xl font-semibold text-ink">Connexion</h2>
      <p className="text-ink/60 text-sm mt-2 mb-8">
        Connectez-vous avec votre identifiant institutionnel.
      </p>

          <form onSubmit={handleSubmit} className="space-y-4" noValidate>
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-ink/80 mb-1.5">
                Email institutionnel
              </label>
              <div className="relative">
                <IconMail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink/35" />
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  placeholder="prenom.nom@universite.ma"
                  className="w-full pl-10 pr-3 py-2.5 border border-ink/15 rounded-lg bg-surface text-sm placeholder:text-ink/30 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal transition-colors"
                />
              </div>
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-ink/80 mb-1.5">
                Mot de passe
              </label>
              <div className="relative">
                <IconLock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink/35" />
                <input
                  id="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  placeholder="********"
                  className="w-full pl-10 pr-10 py-2.5 border border-ink/15 rounded-lg bg-surface text-sm placeholder:text-ink/30 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal transition-colors"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-ink/35 hover:text-ink/60 transition-colors"
                >
                  {showPassword ? <IconEyeOff className="w-4 h-4" /> : <IconEye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div className="pt-1">
              <div className="flex items-center justify-between text-sm">
                <label className="flex items-center gap-2 text-ink/70 select-none">
                  <input
                    type="checkbox"
                    checked={remember}
                    onChange={(e) => setRemember(e.target.checked)}
                    className="rounded border-ink/25 text-signal focus:ring-signal"
                  />
                  Se souvenir de moi
                </label>
                <Link
                  to="/forgot-password"
                  className="font-medium text-signal transition-colors hover:underline"
                >
                  Mot de passe oublié ?
                </Link>
              </div>
              <p className="text-xs text-ink/45 mt-1.5 leading-relaxed">
                {remember
                  ? 'Votre session et votre adresse e-mail seront conservées sur cet ordinateur.'
                  : "Votre session sera fermée lorsque vous quitterez le navigateur, et votre adresse ne sera pas mémorisée."}
              </p>
            </div>

            {error && (
              <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={isSubmitting}
              className="w-full flex items-center justify-center gap-2 bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 text-white py-2.5 rounded-lg font-medium text-sm transition-colors"
            >
              {isSubmitting ? (
                <span className="w-4 h-4 border-2 border-white/40 border-t-white rounded-full animate-spin" />
              ) : (
                <>
                  Se connecter
                  <IconArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>
    </AuthShell>
  )
}