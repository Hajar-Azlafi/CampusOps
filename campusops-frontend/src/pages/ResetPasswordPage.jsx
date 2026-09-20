import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import AuthShell from '../components/layout/AuthShell'
import { resetPassword } from '../api/authApi'
import {
  IconAlertTriangle,
  IconArrowRight,
  IconCheckCircle,
  IconChevronLeft,
  IconEye,
  IconEyeOff,
  IconLock,
} from '../components/icons'

/** Règles appliquées côté backend (PasswordService) — rappelées ici pour l'utilisateur. */
const RULES = [
  { label: 'Au moins 8 caractères', test: (v) => v.length >= 8 },
  { label: 'Au moins une lettre', test: (v) => /[a-zA-Z]/.test(v) },
  { label: 'Au moins un chiffre', test: (v) => /\d/.test(v) },
]

/**
 * Parcours « mot de passe oublié » — étape 2 : définition du nouveau mot de
 * passe à partir du jeton reçu par e-mail (`/reset-password?token=...`).
 *
 * Le jeton est à usage unique et expirant : toute erreur renvoyée par le
 * backend (jeton inconnu, déjà utilisé, expiré) aboutit au même message, sans
 * révéler laquelle des trois causes s'applique.
 */
export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const token = searchParams.get('token') || ''

  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const rulesOk = RULES.every((rule) => rule.test(newPassword))
  const matches = newPassword.length > 0 && newPassword === confirmPassword

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    if (!rulesOk) {
      setError("Le nouveau mot de passe ne respecte pas les règles de sécurité.")
      return
    }
    if (!matches) {
      setError("Les deux mots de passe saisis ne correspondent pas.")
      return
    }
    setIsSubmitting(true)
    try {
      await resetPassword({ token, newPassword, confirmPassword })
      setDone(true)
      setTimeout(() => navigate('/login', { replace: true }), 3000)
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Le lien de réinitialisation est invalide ou a expiré. Demandez-en un nouveau.",
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  // Cas 1 : arrivée sur la page sans jeton (lien tronqué, accès direct).
  if (!token) {
    return (
      <AuthShell>
        <div className="flex gap-3 rounded-lg border border-red-600/25 bg-red-50 px-4 py-3">
          <IconAlertTriangle className="w-5 h-5 shrink-0 text-red-700 mt-0.5" />
          <div className="text-sm text-red-900">
            <p className="font-medium">Lien incomplet</p>
            <p className="text-red-900/80 mt-1">
              Ce lien de réinitialisation ne contient pas de jeton valide. Ouvrez-le
              directement depuis l'e-mail reçu, ou demandez un nouveau lien.
            </p>
          </div>
        </div>
        <Link
          to="/forgot-password"
          className="mt-6 inline-flex items-center gap-1.5 text-sm text-signal transition hover:underline"
        >
          Demander un nouveau lien
          <IconArrowRight className="w-4 h-4" />
        </Link>
      </AuthShell>
    )
  }

  // Cas 2 : réinitialisation réussie.
  if (done) {
    return (
      <AuthShell>
        <div className="flex gap-3 rounded-lg border border-emerald-600/25 bg-emerald-50 px-4 py-3">
          <IconCheckCircle className="w-5 h-5 shrink-0 text-emerald-700 mt-0.5" />
          <div className="text-sm text-emerald-900">
            <p className="font-medium">Mot de passe réinitialisé</p>
            <p className="text-emerald-900/80 mt-1">
              Vous pouvez maintenant vous connecter avec votre nouveau mot de passe.
              Redirection automatique vers la page de connexion…
            </p>
          </div>
        </div>
        <Link
          to="/login"
          className="mt-6 inline-flex items-center gap-2 rounded-lg bg-signal px-4 py-2.5 text-sm font-medium text-white transition hover:bg-signal/90"
        >
          Aller à la connexion
          <IconArrowRight className="w-4 h-4" />
        </Link>
      </AuthShell>
    )
  }

  // Cas 3 : formulaire de saisie.
  return (
    <AuthShell>
      <div className="mb-8">
        <span className="font-mono text-[11px] tracking-[0.18em] text-signal uppercase">
          Réinitialisation
        </span>
        <h2 className="font-display text-3xl font-semibold text-ink mt-2 tracking-tight">
          Nouveau mot de passe
        </h2>
        <p className="text-ink/55 text-sm mt-2 leading-relaxed">
          Choisissez un nouveau mot de passe pour votre compte CampusOps. Ce lien
          ne pourra plus être réutilisé ensuite.
        </p>
      </div>

      {error && (
        <div className="mb-6 flex gap-3 rounded-lg border border-red-600/25 bg-red-50 px-4 py-3">
          <IconAlertTriangle className="w-5 h-5 shrink-0 text-red-700 mt-0.5" />
          <p className="text-sm text-red-900">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        <div>
          <label htmlFor="newPassword" className="block text-xs font-medium text-ink/70 mb-1.5 tracking-wide">
            Nouveau mot de passe
          </label>
          <div className="relative">
            <IconLock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink/35" />
            <input
              id="newPassword"
              type={showPassword ? 'text' : 'password'}
              required
              autoComplete="new-password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full rounded-lg border border-ink/15 bg-surface pl-9 pr-10 py-2.5 text-sm text-ink focus:border-signal focus:outline-none focus:ring-2 focus:ring-signal/20"
            />
            <button
              type="button"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-ink/35 transition hover:text-ink/60"
            >
              {showPassword ? <IconEyeOff className="w-4 h-4" /> : <IconEye className="w-4 h-4" />}
            </button>
          </div>

          <ul className="mt-3 space-y-1">
            {RULES.map((rule) => {
              const ok = rule.test(newPassword)
              return (
                <li
                  key={rule.label}
                  className={`flex items-center gap-2 text-xs ${ok ? 'text-emerald-700' : 'text-ink/45'}`}
                >
                  <span className={`h-1.5 w-1.5 rounded-full ${ok ? 'bg-emerald-600' : 'bg-ink/25'}`} />
                  {rule.label}
                </li>
              )
            })}
          </ul>
        </div>

        <div>
          <label htmlFor="confirmPassword" className="block text-xs font-medium text-ink/70 mb-1.5 tracking-wide">
            Confirmer le nouveau mot de passe
          </label>
          <div className="relative">
            <IconLock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink/35" />
            <input
              id="confirmPassword"
              type={showPassword ? 'text' : 'password'}
              required
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full rounded-lg border border-ink/15 bg-surface pl-9 pr-3 py-2.5 text-sm text-ink focus:border-signal focus:outline-none focus:ring-2 focus:ring-signal/20"
            />
          </div>
          {confirmPassword.length > 0 && !matches && (
            <p className="mt-1.5 text-xs text-red-700">Les deux mots de passe ne correspondent pas.</p>
          )}
        </div>

        <button
          type="submit"
          disabled={isSubmitting || !rulesOk || !matches}
          className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-signal px-4 py-2.5 text-sm font-medium text-white transition hover:bg-signal/90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? 'Enregistrement…' : 'Définir le nouveau mot de passe'}
          {!isSubmitting && <IconArrowRight className="w-4 h-4" />}
        </button>
      </form>

      <Link
        to="/login"
        className="mt-6 inline-flex items-center gap-1.5 text-sm text-ink/55 transition hover:text-signal"
      >
        <IconChevronLeft className="w-4 h-4" />
        Retour à la connexion
      </Link>
    </AuthShell>
  )
}
