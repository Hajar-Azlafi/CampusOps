import axiosClient from './axiosClient'

/**
 * Endpoints publics d'authentification liés au mot de passe.
 * Ces deux routes ne nécessitent aucun jeton : elles sont utilisées avant la
 * connexion (parcours « mot de passe oublié »).
 */

/**
 * Demande un lien de réinitialisation. La réponse est volontairement générique :
 * le serveur ne révèle jamais si l'adresse existe ou non.
 */
export const forgotPassword = (email) =>
  axiosClient.post('/auth/forgot-password', { email }).then((res) => res.data)

/** Définit un nouveau mot de passe à partir du jeton reçu par e-mail. */
export const resetPassword = ({ token, newPassword, confirmPassword }) =>
  axiosClient
    .post('/auth/reset-password', { token, newPassword, confirmPassword })
    .then((res) => res.data)
