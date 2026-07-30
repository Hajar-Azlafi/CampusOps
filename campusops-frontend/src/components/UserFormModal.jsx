import { useState, useEffect } from 'react'
import { createUser, updateUser } from '../api/usersApi'
import { ROLES } from '../constants/roles'
import { IconX } from './icons'

const emptyForm = { firstName: '', lastName: '', email: '', role: '', department: '', phoneNumber: '' }

export default function UserFormModal({ open, mode, initialData, onClose, onSuccess }) {
  const [form, setForm] = useState(emptyForm)
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  useEffect(() => {
    if (mode === 'edit' && initialData) {
      setForm({
        firstName: initialData.firstName ?? '',
        lastName: initialData.lastName ?? '',
        email: initialData.email ?? '',
        role: initialData.role ?? '',
        department: initialData.department ?? '',
        phoneNumber: initialData.phoneNumber ?? '',
      })
    } else {
      setForm(emptyForm)
    }
    setError('')
  }, [open, mode, initialData])

  if (!open) return null

  const handleChange = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setIsSubmitting(true)

    const payload = {
      ...form,
      department: form.department || null,
      phoneNumber: form.phoneNumber || null,
    }

    try {
      if (mode === 'create') {
        const created = await createUser(payload)
        onSuccess(created, created.temporaryPassword)
      } else {
        const updated = await updateUser(initialData.id, payload)
        onSuccess(updated, null)
      }
    } catch (err) {
      if (err.response?.status === 409) {
        setError('Cette adresse email est deja utilisee')
      } else if (err.response?.data?.validationErrors?.length) {
        setError(err.response.data.validationErrors.join(' — '))
      } else {
        setError('Une erreur est survenue, veuillez reessayer')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-white rounded-xl shadow-xl w-full max-w-lg p-6 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between mb-6">
          <h3 className="font-display text-lg font-semibold text-ink">
            {mode === 'create' ? 'Nouvel utilisateur' : "Modifier l'utilisateur"}
          </h3>
          <button onClick={onClose} className="text-ink/40 hover:text-ink/70">
            <IconX className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Prenom</label>
              <input
                required
                value={form.firstName}
                onChange={handleChange('firstName')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Nom</label>
              <input
                required
                value={form.lastName}
                onChange={handleChange('lastName')}
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Email institutionnel</label>
            <input
              type="email"
              required
              value={form.email}
              onChange={handleChange('email')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-ink/80 mb-1.5">Role</label>
            <select
              required
              value={form.role}
              onChange={handleChange('role')}
              className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
            >
              <option value="" disabled>Selectionner un role</option>
              {ROLES.map((r) => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Departement</label>
              <input
                value={form.department}
                onChange={handleChange('department')}
                placeholder="Optionnel"
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-ink/80 mb-1.5">Telephone</label>
              <input
                value={form.phoneNumber}
                onChange={handleChange('phoneNumber')}
                placeholder="Optionnel"
                className="w-full px-3 py-2.5 border border-ink/15 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-signal focus:border-signal"
              />
            </div>
          </div>

          {error && (
            <p role="alert" className="text-sm text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2.5">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-medium text-ink/70 hover:bg-ink/5 rounded-lg transition-colors">
              Annuler
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="px-4 py-2 text-sm font-medium text-white bg-blueprint-800 hover:bg-blueprint-700 disabled:opacity-50 rounded-lg transition-colors"
            >
              {isSubmitting ? '...' : mode === 'create' ? 'Creer' : 'Enregistrer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}