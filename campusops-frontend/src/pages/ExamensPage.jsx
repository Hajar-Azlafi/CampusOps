import { Navigate } from 'react-router-dom'

// L'ancienne page « Examens » n'existe plus comme module autonome : la gestion
// des examens est devenue la première sous-partie du module « Occupation
// supplémentaire », aux côtés des soutenances et des autres occupations. La
// liste elle-même vit désormais dans `components/occupations/ExamensTab.jsx`,
// afin qu'il n'en existe qu'un seul exemplaire.
//
// Ce fichier ne conserve qu'une redirection : tout lien historique vers
// /examens (favori, notification, e-mail envoyé avant la refonte) continue
// d'aboutir sur le bon onglet au lieu de tomber sur une page inconnue.
export default function ExamensPage() {
  return <Navigate to="/occupations?onglet=examens" replace />
}
