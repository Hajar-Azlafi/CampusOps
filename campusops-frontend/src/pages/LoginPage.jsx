import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

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

function BlueprintFloorplan() {
  return (
    <svg viewBox="0 0 420 260" className="w-full max-w-sm" aria-hidden="true">
      <rect x="30" y="20" width="360" height="200" rx="6"
        className="fill-white/[0.02] stroke-blueprint-line/30" strokeWidth="1.5" />
      <rect x="30" y="100" width="360" height="40" className="fill-white/[0.03]" />
      <line x1="35" y1="120" x2="385" y2="120"
        className="stroke-blueprint-line/20" strokeWidth="1" strokeDasharray="4 4" />

      <line x1="150" y1="20" x2="150" y2="100" className="stroke-blueprint-line/30" strokeWidth="1.5" />
      <line x1="270" y1="20" x2="270" y2="100" className="stroke-blueprint-line/30" strokeWidth="1.5" />
      <line x1="150" y1="140" x2="150" y2="220" className="stroke-blueprint-line/30" strokeWidth="1.5" />
      <line x1="270" y1="140" x2="270" y2="220" className="stroke-blueprint-line/30" strokeWidth="1.5" />

      <rect x="32" y="22" width="116" height="76" rx="2"
        className="fill-signal/10 stroke-signal/60 motion-safe:animate-pulse" strokeWidth="1.5" />
      <circle cx="138" cy="32" r="3" className="fill-signal motion-safe:animate-pulse" />
      <text x="42" y="42" className="fill-signal-light font-mono" fontSize="9" letterSpacing="0.5">A-01</text>
      <text x="42" y="54" className="fill-blueprint-line/70 font-mono" fontSize="7">Libre</text>
      <path d="M80,100 A20,20 0 0 1 100,80" className="fill-none stroke-blueprint-line/25" strokeWidth="1" />

      <rect x="272" y="142" width="116" height="76" rx="2"
        className="fill-signal/10 stroke-signal/60 motion-safe:animate-pulse" strokeWidth="1.5" />
      <circle cx="378" cy="152" r="3" className="fill-signal motion-safe:animate-pulse" />
      <text x="282" y="162" className="fill-signal-light font-mono" fontSize="9" letterSpacing="0.5">C-18</text>
      <text x="282" y="174" className="fill-blueprint-line/70 font-mono" fontSize="7">Libre</text>
      <path d="M320,140 A20,20 0 0 0 340,160" className="fill-none stroke-blueprint-line/25" strokeWidth="1" />

      <g className="stroke-blueprint-line/30" strokeWidth="1" fill="none">
        <circle cx="395" cy="40" r="13" />
        <polygon points="395,29 398,41 392,41" className="fill-blueprint-line/40" />
      </g>
      <text x="395" y="14" textAnchor="middle" className="fill-blueprint-line/50 font-mono" fontSize="8">N</text>
    </svg>
  )
}

export default function LoginPage() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [remember, setRemember] = useState(true)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setIsSubmitting(true)

    try {
      const userData = await login(email, password, remember)
      navigate(userData.mustChangePassword ? '/change-password' : '/dashboard')
    } catch (err) {
      if (err.response?.status === 401) {
        setError('Identifiants incorrects')
      } else if (err.response?.status === 403) {
        setError('Ce compte est desactive')
      } else {
        setError('Une erreur est survenue, veuillez reessayer')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex flex-col lg:flex-row font-body">

      {/* Panneau gauche */}
      <div className="relative lg:w-1/2 bg-gradient-to-br from-blueprint-800 to-blueprint-900 text-white px-8 py-10 lg:px-16 lg:py-16 flex flex-col justify-between overflow-hidden">
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.07]"
          style={{
            backgroundImage:
              'linear-gradient(var(--color-blueprint-line) 1px, transparent 1px), linear-gradient(90deg, var(--color-blueprint-line) 1px, transparent 1px)',
            backgroundSize: '32px 32px',
          }}
        />

        <div className="relative">
          <span className="font-mono text-[11px] tracking-[0.2em] text-signal-light uppercase">
            Systeme de gestion des espaces
          </span>
          <h1 className="font-display text-4xl lg:text-5xl font-semibold mt-3 tracking-tight">
            Campus<span className="text-signal-light">Ops</span>
          </h1>
          <p className="text-blueprint-line/80 mt-3 max-w-sm text-sm lg:text-base">
            Reservez et gerez les salles, amphis et laboratoires de votre etablissement en temps reel.
          </p>
        </div>

        <div className="relative flex justify-center py-8">
          <BlueprintFloorplan />
        </div>

        <div className="relative flex items-center gap-6 font-mono text-xs text-blueprint-line/70 border-t border-white/10 pt-5">
          <span><span className="text-signal-light font-medium">18+</span> Bâtiments</span>
          <span className="w-px h-4 bg-white/10" />
          <span><span className="text-signal-light font-medium">300+</span> Espaces</span>
          <span className="w-px h-4 bg-white/10" />
          <span>Disponibilité en temps réel</span>
        </div>
      </div>

      {/* Panneau droit */}
      <div className="lg:w-1/2 bg-paper flex items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
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
                  className="w-full pl-10 pr-3 py-2.5 border border-ink/15 rounded-lg bg-white text-sm placeholder:text-ink/30 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal transition-colors"
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
                  className="w-full pl-10 pr-10 py-2.5 border border-ink/15 rounded-lg bg-white text-sm placeholder:text-ink/30 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal transition-colors"
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

            <div className="flex items-center justify-between text-sm pt-1">
              <label className="flex items-center gap-2 text-ink/70 select-none">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                  className="rounded border-ink/25 text-signal focus:ring-signal"
                />
                Se souvenir de moi
              </label>
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

          <p className="text-xs text-ink/45 text-center mt-6 leading-relaxed">
            Mot de passe oublié ? Contactez l'administrateur de votre établissement.
          </p>

          <div className="mt-10 pt-6 border-t border-ink/10 text-center">
            <p className="text-xs text-ink/40">
              © 2026 CampusOps · Tous droits réservés
            </p>
            <p className="font-mono text-[10px] text-ink/30 mt-1 tracking-wide">
              Gestion et réservation des espaces pédagogiques
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}