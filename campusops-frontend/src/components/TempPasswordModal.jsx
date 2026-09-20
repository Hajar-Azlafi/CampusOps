import { useState } from 'react'
import { IconClipboard, IconEye, IconEyeOff } from './icons'

export default function TempPasswordModal({ open, email, password, emailSent = false, onClose }) {
  const [copied, setCopied] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  if (!open) return null

  const handleCopy = async () => {
    await navigator.clipboard.writeText(password)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" />
      <div className="relative bg-surface rounded-xl shadow-xl w-full max-w-sm p-6">
        <h3 className="font-display text-lg font-semibold text-ink mb-1">Mot de passe temporaire</h3>
        <p className="text-sm text-ink/60 mb-4">
          {emailSent
            ? 'Un e-mail contenant le nouveau mot de passe a été envoyé à cet utilisateur.'
            : "L'e-mail n'a pas pu être envoyé. Utilisez temporairement ce mot de passe pour l'utilisateur."}
        </p>

        <div className="bg-ink/5 rounded-lg p-4 space-y-2 mb-4">
          <div>
            <p className="text-xs text-ink/40 font-mono uppercase tracking-wide">Email</p>
            <p className="text-sm text-ink font-medium">{email}</p>
          </div>
          <div>
            <p className="text-xs text-ink/40 font-mono uppercase tracking-wide">Mot de passe temporaire</p>
            <div className="flex items-center gap-2">
              <p className="min-w-0 flex-1 truncate text-sm text-ink font-mono font-medium">
                {showPassword ? password : '•'.repeat(password.length)}
              </p>
              <button
                type="button"
                onClick={() => setShowPassword((value) => !value)}
                aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                className="shrink-0 text-ink/45 hover:text-ink/70 transition-colors"
              >
                {showPassword ? <IconEyeOff className="w-4 h-4" /> : <IconEye className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>

        <div className="flex gap-3">
          <button
            type="button"
            onClick={handleCopy}
            className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2 text-sm font-medium text-heading border border-heading/30 rounded-lg hover:bg-heading/5 transition-colors"
          >
            <IconClipboard className="w-4 h-4" />
            {copied ? 'Copié !' : 'Copier'}
          </button>
          <button
            onClick={onClose}
            className="flex-1 px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 rounded-lg transition-colors"
          >
            Fermer
          </button>
        </div>
      </div>
    </div>
  )
}