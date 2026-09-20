import { useCallback, useMemo, useState } from 'react'

/**
 * État local d'une section de la page Paramètres (un onglet = un ou plusieurs
 * formulaires indépendants, chacun avec son « Enregistrer / Annuler »).
 *
 * Le hook applique la convention de validation de CampusOps : `validate` est
 * rejoué à CHAQUE frappe, l'erreur s'affiche immédiatement sous le champ et le
 * bouton « Enregistrer » reste désactivé tant que la saisie est invalide. Rien
 * n'est envoyé au serveur pour être refusé ensuite.
 *
 * `onSave(values)` appartient à l'onglet : il construit la charge utile, appelle
 * l'API et peut renvoyer les nouvelles valeurs du formulaire (celles renvoyées
 * par le serveur, qui a pu normaliser la saisie. Sans valeur de retour, la
 * saisie courante devient la référence.
 */
export function useSectionForm({ initial, validate, onSave }) {
  const [values, setValues] = useState(initial)
  // Référence : valeurs enregistrées côté serveur. Sert au calcul de « modifié »
  // et au bouton « Annuler ».
  const [reference, setReference] = useState(initial)
  const [saving, setSaving] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [notice, setNotice] = useState('')

  const errors = useMemo(() => validate(values) ?? {}, [validate, values])
  const valid = Object.keys(errors).length === 0
  const dirty = useMemo(() => !sameForm(values, reference), [values, reference])

  /** Modifie un champ ; efface les bandeaux, devenus obsolètes. */
  const setField = useCallback((name, value) => {
    setNotice('')
    setErrorMsg('')
    setValues((previous) => ({ ...previous, [name]: value }))
  }, [])

  const reset = useCallback(() => {
    setValues(reference)
    setNotice('')
    setErrorMsg('')
  }, [reference])

  /** Recharge la référence depuis le serveur (après un rechargement global). */
  const adopt = useCallback((next) => {
    setValues(next)
    setReference(next)
  }, [])

  const submit = useCallback(
    (event) => {
      event?.preventDefault()
      if (!dirty || !valid || saving) return
      setSaving(true)
      setErrorMsg('')
      setNotice('')
      Promise.resolve()
        .then(() => onSave(values))
        .then((saved) => {
          const next = saved ?? values
          setValues(next)
          setReference(next)
          setNotice('Paramètres enregistrés.')
        })
        .catch((err) => {
          // Message du serveur tel qu'il l'a formulé (règles croisées, fuseau
          // inconnu…), sinon repli générique.
          setErrorMsg(
            err.response?.data?.message ||
              'Enregistrement impossible. Vérifiez votre connexion puis réessayez.'
          )
        })
        .finally(() => setSaving(false))
    },
    [dirty, valid, saving, onSave, values]
  )

  return {
    values,
    setField,
    errors,
    valid,
    dirty,
    saving,
    errorMsg,
    notice,
    submit,
    reset,
    adopt,
    setErrorMsg,
    setNotice,
  }
}

function sameValue(a, b) {
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, index) => item === b[index])
  }
  return a === b
}

/** Comparaison champ à champ, tolérante aux tableaux (jours, formats). */
function sameForm(a, b) {
  const keys = new Set([...Object.keys(a ?? {}), ...Object.keys(b ?? {})])
  for (const key of keys) {
    if (!sameValue(a?.[key], b?.[key])) return false
  }
  return true
}
