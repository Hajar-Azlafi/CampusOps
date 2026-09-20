import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthShell from '../components/layout/AuthShell'
import { forgotPassword } from '../api/authApi'
import { IconAlertTriangle, IconArrowRight, IconCheckCircle, IconChevronLeft } from '../components/icons'


function IconMail(props) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"
      strokeLinecap="round" strokeLinejoin="round" {...props}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m3 7 9 6 9-6" />
    </svg>
  )
}

/**
 * Parcours « mot de passe oublié » — étape 1 : saisie de l'adresse e-mail.
 *
 * Le backend renvoie toujours le même message, qu'un compte existe ou non
 * (protection contre l'énumération d'adresses). L'écran reflète ce contrat :
 * en cas de succès on affiche la confirmation générique sans jamais indiquer
 * si l'adresse est connue du système.
 */
export default function ForgotPasswordPage() {
  // Meme cle que LoginPage : si l'utilisateur a coche « Se souvenir de moi »,
  // son adresse est deja connue et evite une saisie inutile.
  const [email, setEmail] = useState(
    () => localStorage.getItem('campusops.rememberedEmail') || '',
  )
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const handleSubmit = async (event) => {
    event.preventDefault()
    setError('')
    setMessage('')
    setIsSubmitting(true)
    try {
      const data = await forgotPassword(email.trim().toLowerCase())
      // Backend returns a MessageResponseDto { message: string }.
      const respMessage = data?.message || "Si un compte correspondant à cette adresse existe, un e-mail de réinitialisation a été envoyé."
      const NOT_FOUND_TEXT = "Aucun compte n'est associé à cette adresse e-mail.";

      if (respMessage === NOT_FOUND_TEXT) {
        // Show explicit not-found as an error (red) per user request.
        setError(respMessage)
        setMessage('')
      } else {
        setMessage(respMessage)
        setError('')
      }
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Impossible de traiter la demande pour le moment. Réessayez dans quelques instants.",
      )
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell>
      <div className="mb-8">
        <span className="font-mono text-[11px] tracking-[0.18em] text-signal uppercase">
          Réinitialisation
        </span>
        <h2 className="font-display text-3xl font-semibold text-ink mt-2 tracking-tight">
          Mot de passe oublié
        </h2>
        <p className="text-ink/55 text-sm mt-2 leading-relaxed">
          Indiquez l'adresse e-mail de votre compte CampusOps. Nous vous enverrons
          un lien sécurisé pour définir un nouveau mot de passe.
        </p>
      </div>

      {message && (
        <div className="mb-6 flex gap-3 rounded-lg border border-emerald-600/25 bg-emerald-50 px-4 py-3">
          <IconCheckCircle className="w-5 h-5 shrink-0 text-emerald-700 mt-0.5" />
          <div className="text-sm text-emerald-900">
            <p>{message}</p>
          </div>
        </div>
      )}

      {error && (
        <div className="mb-6 flex gap-3 rounded-lg border border-red-600/25 bg-red-50 px-4 py-3">
          <IconAlertTriangle className="w-5 h-5 shrink-0 text-red-700 mt-0.5" />
          <p className="text-sm text-red-900">{error}</p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        <div>
          <label htmlFor="email" className="block text-xs font-medium text-ink/70 mb-1.5 tracking-wide">
            Adresse e-mail
          </label>
          <div className="relative">
            <IconMail className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink/35" />
            <input
              id="email"
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="prenom.nom@campusops.ma"
              className="w-full rounded-lg border border-ink/15 bg-surface pl-9 pr-3 py-2.5 text-sm text-ink placeholder:text-ink/30 focus:border-signal focus:outline-none focus:ring-2 focus:ring-signal/20"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isSubmitting || !email.trim()}
          className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-signal px-4 py-2.5 text-sm font-medium text-white transition hover:bg-signal/90 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {isSubmitting ? 'Envoi en cours…' : 'Envoyer le lien de réinitialisation'}
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
