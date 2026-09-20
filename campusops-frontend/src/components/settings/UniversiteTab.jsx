import { useCallback } from 'react'
import { Link } from 'react-router-dom'
import { updateUniversity } from '../../api/settingsApi'
import { TIMEZONE_SUGGESTIONS } from '../../constants/settingsOptions'
import {
  emailFacultatif,
  siteWebFacultatif,
  telephoneFacultatif,
  texteFacultatif,
  texteObligatoire,
} from '../../utils/settingsValidation'
import BrandingMedia from './BrandingMedia'
import {
  ErrorBanner,
  SaveBar,
  SettingsSection,
  SuccessBanner,
  TextField,
  labelClass,
} from './fields'
import { useSectionForm } from './useSectionForm'

// Onglet « Université » : identité de l'établissement (§16), identité visuelle
// (§11), et rappel EN LECTURE SEULE du contexte académique — l'année active et
// les semestres courants restent pilotés par leurs modules (§4), cette page ne
// fait que les afficher et y renvoyer.

const CHAMPS = [
  'nom',
  'nomCourt',
  'slogan',
  'adresse',
  'ville',
  'pays',
  'telephone',
  'email',
  'siteWeb',
  'fuseauHoraire',
]

/** DTO serveur -> champs de formulaire (jamais de `null` dans un input). */
function toForm(dto) {
  const values = {}
  CHAMPS.forEach((champ) => {
    values[champ] = dto?.[champ] ?? ''
  })
  return values
}

function toPayload(values) {
  const payload = {}
  CHAMPS.forEach((champ) => {
    payload[champ] = values[champ].trim()
  })
  return payload
}

function validate(values) {
  const errors = {}
  const ajoute = (champ, message) => {
    if (message) errors[champ] = message
  }
  ajoute('nom', texteObligatoire(values.nom, 150))
  ajoute('nomCourt', texteFacultatif(values.nomCourt, 40))
  ajoute('slogan', texteFacultatif(values.slogan, 180))
  ajoute('adresse', texteFacultatif(values.adresse, 255))
  ajoute('ville', texteFacultatif(values.ville, 100))
  ajoute('pays', texteFacultatif(values.pays, 100))
  ajoute('telephone', telephoneFacultatif(values.telephone))
  ajoute('email', emailFacultatif(values.email))
  ajoute('siteWeb', siteWebFacultatif(values.siteWeb))
  ajoute('fuseauHoraire', texteObligatoire(values.fuseauHoraire, 60))
  return errors
}

function periode(debut, fin) {
  if (!debut && !fin) return null
  return `du ${debut ?? '—'} au ${fin ?? '—'}`
}

/** Contexte académique : consultation seule, avec renvoi vers les modules. */
function AcademicContextCard({ contexte }) {
  const annee = contexte?.anneeActive
  const semestres = contexte?.semestresCourants ?? []
  return (
    <SettingsSection
      title="Contexte académique"
      description="Piloté par les modules Années universitaires et Semestres. Affiché ici pour consultation ; toute modification passe par ces modules, qui appliquent leurs propres règles."
    >
      <div className="grid gap-4 md:grid-cols-2">
        <div>
          <span className={labelClass}>Année universitaire active</span>
          {annee ? (
            <p className="text-sm text-ink/70">
              <span className="font-medium text-heading">{annee.libelle}</span>
              {periode(annee.dateDebut, annee.dateFin) && (
                <span className="text-ink/50"> — {periode(annee.dateDebut, annee.dateFin)}</span>
              )}
            </p>
          ) : (
            <p className="text-sm text-red-600">Aucune année active. Activez-en une pour que les réservations et les emplois du temps soient rattachés à une année.</p>
          )}
          <p className="text-xs text-ink/45 mt-1.5">
            {`${contexte?.nombreAnneesHistorisees ?? 0} année(s) conservée(s) dans l’historique.`}{' '}
            <Link to="/academic-years" className="text-signal-dark hover:underline">
              Gérer les années
            </Link>
          </p>
        </div>
        <div>
          <span className={labelClass}>Semestres courants</span>
          {semestres.length ? (
            <ul className="text-sm text-ink/70 space-y-1">
              {semestres.map((semestre) => (
                <li key={semestre.id}>
                  <span className="font-medium text-heading">{semestre.nom}</span>
                  {semestre.levelNom && <span className="text-ink/50"> · {semestre.levelNom}</span>}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-ink/50">Aucun semestre marqué comme courant.</p>
          )}
          <p className="text-xs text-ink/45 mt-1.5">
            <Link to="/semesters" className="text-signal-dark hover:underline">
              Gérer les semestres
            </Link>
          </p>
        </div>
      </div>
    </SettingsSection>
  )
}
/** Onglet « Université » : formulaire d'identité + identité visuelle + contexte. */
export default function UniversiteTab({ settings, onSaved, onMediaChanged }) {
  const onSave = useCallback(
    (values) =>
      updateUniversity(toPayload(values)).then((saved) => {
        onSaved('universite', saved)
        return toForm(saved)
      }),
    [onSaved]
  )

  const form = useSectionForm({ initial: toForm(settings.universite), validate, onSave })
  const { values, setField, errors } = form

  return (
    <>
      <form onSubmit={form.submit} noValidate>
        <SettingsSection
          title="Identité de l’établissement"
          description="Ces informations apparaissent sur la page de connexion, dans le menu et dans l’onglet du navigateur."
        >
          <SuccessBanner message={form.notice} />
          <ErrorBanner message={form.errorMsg} />
          <div className="grid gap-4 md:grid-cols-2">
            <TextField
              label="Nom de l’université"
              required
              maxLength={150}
              value={values.nom}
              onChange={(value) => setField('nom', value)}
              error={errors.nom}
              placeholder="Université Hassan II de Casablanca"
            />
            <TextField
              label="Nom court"
              maxLength={40}
              value={values.nomCourt}
              onChange={(value) => setField('nomCourt', value)}
              error={errors.nomCourt}
              hint="Utilisé dans le menu latéral, où la place est limitée."
            />
            <div className="md:col-span-2">
              <TextField
                label="Slogan"
                maxLength={180}
                value={values.slogan}
                onChange={(value) => setField('slogan', value)}
                error={errors.slogan}
                hint="Affiché sous le nom sur la page de connexion."
              />
            </div>
            <div className="md:col-span-2">
              <TextField
                label="Adresse"
                maxLength={255}
                value={values.adresse}
                onChange={(value) => setField('adresse', value)}
                error={errors.adresse}
              />
            </div>
            <TextField
              label="Ville"
              maxLength={100}
              value={values.ville}
              onChange={(value) => setField('ville', value)}
              error={errors.ville}
            />
            <TextField
              label="Pays"
              maxLength={100}
              value={values.pays}
              onChange={(value) => setField('pays', value)}
              error={errors.pays}
            />
            <TextField
              label="Téléphone"
              value={values.telephone}
              onChange={(value) => setField('telephone', value)}
              error={errors.telephone}
              placeholder="+212 522 00 00 00"
            />
            <TextField
              label="Adresse e-mail"
              type="email"
              maxLength={150}
              value={values.email}
              onChange={(value) => setField('email', value)}
              error={errors.email}
              placeholder="contact@universite.ma"
            />
            <div className="md:col-span-2">
              <TextField
                label="Site web"
                value={values.siteWeb}
                onChange={(value) => setField('siteWeb', value)}
                error={errors.siteWeb}
                placeholder="https://www.universite.ma"
              />
            </div>
            <TextField
              label="Fuseau horaire"
              required
              value={values.fuseauHoraire}
              onChange={(value) => setField('fuseauHoraire', value)}
              error={errors.fuseauHoraire}
              suggestions={TIMEZONE_SUGGESTIONS}
              hint="Identifiant IANA. Exemple : Africa/Casablanca"
            />
          </div>
          <SaveBar
            dirty={form.dirty}
            valid={form.valid}
            saving={form.saving}
            onCancel={form.reset}
          />
        </SettingsSection>
      </form>

      <BrandingMedia
        logo={settings.logo}
        favicon={settings.favicon}
        onChanged={onMediaChanged}
      />

      <AcademicContextCard contexte={settings.contexteAcademique} />
    </>
  )
}
