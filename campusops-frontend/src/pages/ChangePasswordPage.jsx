import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import axiosClient from '../api/axiosClient'
import { useAuth } from '../context/AuthContext'
import { IconLock, IconEye, IconEyeOff } from '../components/icons'

function PasswordField({ id, label, value, onChange, show, onToggleShow, autoComplete }) {
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-ink/80 mb-1.5">
        {label}
      </label>
      <div className="relative">
        <IconLock className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-ink/35" />
        <input
          id={id}
          type={show ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          onChange={onChange}
          required
          className="w-full pl-10 pr-10 py-2.5 border border-ink/15 rounded-lg bg-white text-sm placeholder:text-ink/30 focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal transition-colors"
        />
        <button
          type="button"
          onClick={onToggleShow}
          aria-label={show ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
          className="absolute right-3 top-1/2 -translate-y-1/2 text-ink/35 hover:text-ink/60 transition-colors"
        >
          {show ? <IconEyeOff className="w-4 h-4" /> : <IconEye className="w-4 h-4" />}
        </button>
      </div>
    </div>
  )
}

export default function ChangePasswordPage() {
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showOld, setShowOld] = useState(false)
  const [showNew, setShowNew] = useState(false)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  const { user, markPasswordChanged } = useAuth()
  const navigate = useNavigate()

  const isForced = user?.mustChangePassword

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')

    if (newPassword.length < 8) {
      setError('Le nouveau mot de passe doit contenir au moins 8 caracteres')
      return
    }
    if (newPassword !== confirmPassword) {
      setError('Les deux mots de passe ne correspondent pas')
      return
    }

    setIsSubmitting(true)
    try {
      await axiosClient.put('/auth/change-password', { oldPassword, newPassword })
      markPasswordChanged()
      navigate('/dashboard')
    } catch (err) {
      if (err.response?.status === 401) {
        setError("L'ancien mot de passe est incorrect")
      } else {
        setError('Une erreur est survenue, veuillez reessayer')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="max-w-md">
      {isForced && (
        <div className="mb-6 bg-signal/10 border border-signal/30 rounded-lg px-4 py-3">
          <p className="text-sm text-blueprint-800 font-medium">
            Vous devez changer votre mot de passe temporaire avant de continuer.
          </p>
        </div>
      )}

      <h1 className="font-display text-2xl font-semibold text-ink mb-1">
        Changer le mot de passe
      </h1>
      <p className="text-ink/60 text-sm mb-8">
        Choisissez un nouveau mot de passe pour votre compte.
      </p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <PasswordField
          id="oldPassword"
          label="Mot de passe actuel"
          value={oldPassword}
          onChange={(e) => setOldPassword(e.target.value)}
          show={showOld}
          onToggleShow={() => setShowOld((v) => !v)}
          autoComplete="current-password"
        />
        <PasswordField
          id="newPassword"
          label="Nouveau mot de passe"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          show={showNew}
          onToggleShow={() => setShowNew((v) => !v)}
          autoComplete="new-password"
        />
        <PasswordField
          id="confirmPassword"
          label="Confirmer le nouveau mot de passe"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          show={showNew}
          onToggleShow={() => setShowNew((v) => !v)}
          autoComplete="new-password"
        />

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
            'Mettre a jour le mot de passe'
          )}
        </button>
      </form>
    </div>
  )
}