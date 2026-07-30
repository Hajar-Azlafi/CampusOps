import { useState } from 'react'

export default function TempPasswordModal({ open, email, password, onClose }) {
  const [copied, setCopied] = useState(false)
  if (!open) return null

  const handleCopy = async () => {
    await navigator.clipboard.writeText(password)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" />
      <div className="relative bg-white rounded-xl shadow-xl w-full max-w-sm p-6">
        <h3 className="font-display text-lg font-semibold text-ink mb-1">Mot de passe temporaire</h3>
        <p className="text-sm text-ink/60 mb-4">
          Communiquez ces identifiants a l'utilisateur. Ce mot de passe ne sera plus jamais affiche.
        </p>

        <div className="bg-ink/5 rounded-lg p-4 space-y-2 mb-4">
          <div>
            <p className="text-xs text-ink/40 font-mono uppercase tracking-wide">Email</p>
            <p className="text-sm text-ink font-medium">{email}</p>
          </div>
          <div>
            <p className="text-xs text-ink/40 font-mono uppercase tracking-wide">Mot de passe</p>
            <p className="text-sm text-ink font-mono font-medium">{password}</p>
          </div>
        </div>

        <div className="flex gap-3">
          <button
            onClick={handleCopy}
            className="flex-1 px-4 py-2 text-sm font-medium text-blueprint-800 border border-blueprint-800/30 rounded-lg hover:bg-blueprint-800/5 transition-colors"
          >
            {copied ? 'Copie !' : 'Copier'}
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