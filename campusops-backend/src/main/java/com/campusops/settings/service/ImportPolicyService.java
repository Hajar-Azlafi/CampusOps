package com.campusops.settings.service;

import com.campusops.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * Garde-fou commun aux <b>cinq</b> imports Excel existants (§9) : utilisateurs,
 * emplois du temps, seances, examens, occupations supplementaires.
 *
 * <h2>Pourquoi un service dedie</h2>
 * <p>Le cahier des charges interdit de creer un second systeme d'import : ce
 * service ne lit ni n'ecrit aucune donnee metier, il ne fait qu'<b>appliquer la
 * politique configuree</b> avant que l'import existant ne commence son travail.
 * Chaque importeur l'appelle en premiere ligne, ce qui evite de dupliquer cinq
 * fois les memes controles et garantit un message d'erreur identique partout.</p>
 *
 * <h2>Controles appliques, dans cet ordre</h2>
 * <ol>
 *   <li><b>Presence</b> : fichier absent ou vide refuse immediatement.</li>
 *   <li><b>Taille</b> : bornee par {@code tailleMaxFichierMo}, elle-meme plafonnee
 *       par la limite technique du conteneur ({@code plafondServeurMo}).</li>
 *   <li><b>Extension</b> : doit figurer dans les formats autorises. Premier
 *       filtre, jamais le seul.</li>
 *   <li><b>Signature binaire</b> : le <b>contenu</b> doit etre un classeur Excel
 *       reellement lisible, et d'un format autorise. Un fichier renomme
 *       {@code archive.zip -> donnees.xlsx} passe l'extension mais est rejete
 *       ici, conformement a la consigne « ne jamais faire confiance uniquement
 *       aux extensions ».</li>
 * </ol>
 *
 * <p>Les deux autres reglages d'import ({@code validationAutomatique} et
 * {@code ecrasementDonneesAutorise}) sont exposes en lecture ci-dessous : c'est
 * l'importeur qui les applique, car seule sa logique sait ce qu'est une « ligne
 * invalide » ou un « enregistrement deja present ».</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportPolicyService {

    private final SettingsService settingsService;

    /**
     * Signature binaire des deux conteneurs qu'Apache POI sait ouvrir. Elle est
     * comparee au debut reel du fichier, independamment de son nom.
     */
    private enum WorkbookKind {

        /** Classeur OOXML ({@code .xlsx}) : archive ZIP, signature « PK ». */
        XLSX("xlsx", new int[]{0x50, 0x4B, 0x03, 0x04}),

        /** Classeur binaire historique ({@code .xls}) : conteneur OLE2. */
        XLS("xls", new int[]{0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1});

        private final String extension;
        private final int[] signature;

        WorkbookKind(String extension, int[] signature) {
            this.extension = extension;
            this.signature = signature;
        }

        boolean matches(byte[] entete) {
            if (entete.length < signature.length) {
                return false;
            }
            for (int i = 0; i < signature.length; i++) {
                if ((entete[i] & 0xFF) != signature[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Nombre d'octets suffisant pour identifier les deux conteneurs. */
    private static final int TAILLE_ENTETE = 8;

    // ===================== Garde-fou de fichier (§9) =====================

    /**
     * Verifie qu'un fichier respecte la politique d'import configuree et leve une
     * {@link BadRequestException} au message explicite dans le cas contraire
     * (traduite en HTTP 400 par le gestionnaire global d'exceptions).
     *
     * @param file    fichier recu du client
     * @param libelle nature de l'import, integree au message (« des utilisateurs »,
     *                « de l'emploi du temps »...) afin que l'administrateur sache
     *                immediatement quel envoi a echoue
     */
    public void verifierFichier(MultipartFile file, String libelle) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Aucun fichier n'a été envoyé pour l'import "
                    + libelle + ".");
        }

        verifierTaille(file);
        List<String> autorises = settingsService.formatsAutorises();
        verifierExtension(file, autorises);
        verifierContenu(file, autorises);
    }

    /**
     * Taille maximale reellement applicable, en megaoctets : la valeur configuree,
     * sans jamais depasser la limite technique du conteneur.
     */
    public int tailleMaxEffectiveMo() {
        int configuree = settingsService.current().getTailleMaxFichierMo() == null
                ? settingsService.plafondServeurMo()
                : settingsService.current().getTailleMaxFichierMo();
        return Math.max(1, Math.min(configuree, settingsService.plafondServeurMo()));
    }

    // ===================== Reglages laisses a l'importeur =====================

    /**
     * Vrai si la <b>validation automatique</b> est active : un fichier comportant
     * au moins une ligne invalide est alors refuse en bloc, aucune ligne n'etant
     * enregistree. Faux : l'import devient tolerant, les lignes valides sont
     * enregistrees et les lignes rejetees sont rapportees.
     */
    public boolean isValidationStricte() {
        return settingsService.current().isValidationAutomatique();
    }

    /**
     * Vrai si l'import peut <b>mettre a jour</b> un enregistrement deja present au
     * lieu de le signaler en doublon.
     *
     * <p>Ce reglage ne s'applique volontairement qu'aux referentiels dont la mise a
     * jour est sans perte (utilisateur identifie par son e-mail). Il n'est jamais
     * utilise pour les seances, examens et occupations : « ecraser » y signifierait
     * supprimer l'occupation d'un autre utilisateur.</p>
     */
    public boolean isEcrasementAutorise() {
        return settingsService.current().isEcrasementDonneesAutorise();
    }

    /**
     * Message unique employe par tous les imports lorsque la validation
     * automatique refuse un fichier : un seul texte, quelle que soit l'origine.
     */
    public String motifValidationStricte(long lignesEnErreur, String objets) {
        return String.format(
                "Validation automatique active : le fichier contient %d ligne(s) en erreur."
                        + " Corrigez-les puis relancez l'import — aucun %s n'a été enregistré.",
                lignesEnErreur, objets);
    }

    // ===================== Interne =====================

    private void verifierTaille(MultipartFile file) {
        int maximumMo = tailleMaxEffectiveMo();
        long maximumOctets = (long) maximumMo * 1024 * 1024;
        if (file.getSize() > maximumOctets) {
            throw new BadRequestException(String.format(
                    "Le fichier %s dépasse la taille maximale autorisée : %s pour un maximum de %d Mo.",
                    nomLisible(file), tailleLisible(file.getSize()), maximumMo));
        }
    }

    private void verifierExtension(MultipartFile file, List<String> autorises) {
        String extension = extensionOf(file.getOriginalFilename());
        if (extension.isEmpty() || !autorises.contains(extension)) {
            throw new BadRequestException(String.format(
                    "Format de fichier non autorisé : %s. Formats acceptés : %s.",
                    extension.isEmpty() ? "extension absente" : "« ." + extension + " »",
                    formatsLisibles(autorises)));
        }
    }

    /**
     * Compare le debut reel du fichier aux signatures connues. Le format deduit du
     * contenu doit lui aussi figurer parmi les formats autorises : un {@code .xls}
     * renomme en {@code .xlsx} est donc refuse si {@code xls} est interdit.
     */
    private void verifierContenu(MultipartFile file, List<String> autorises) {
        byte[] entete = lireEntete(file);
        for (WorkbookKind kind : WorkbookKind.values()) {
            if (kind.matches(entete)) {
                if (!autorises.contains(kind.extension)) {
                    throw new BadRequestException(String.format(
                            "Le contenu du fichier %s est un classeur « .%s », format non autorisé."
                                    + " Formats acceptés : %s.",
                            nomLisible(file), kind.extension, formatsLisibles(autorises)));
                }
                return;
            }
        }
        throw new BadRequestException(String.format(
                "Le fichier %s n'est pas un classeur Excel valide : son contenu ne correspond"
                        + " à aucun format lisible (%s). Vérifiez que le fichier n'a pas été"
                        + " simplement renommé.",
                nomLisible(file), formatsLisibles(settingsService.formatsSupportes())));
    }

    private byte[] lireEntete(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] entete = new byte[TAILLE_ENTETE];
            int lus = in.readNBytes(entete, 0, TAILLE_ENTETE);
            if (lus <= 0) {
                throw new BadRequestException("Le fichier envoyé est vide.");
            }
            if (lus == TAILLE_ENTETE) {
                return entete;
            }
            byte[] partiel = new byte[lus];
            System.arraycopy(entete, 0, partiel, 0, lus);
            return partiel;
        } catch (IOException ex) {
            log.warn("Lecture impossible de l'en-tete du fichier importe : {}", ex.getMessage());
            throw new BadRequestException(
                    "Le fichier n'a pas pu être lu. Veuillez réessayer.");
        }
    }

    private static String extensionOf(String nomFichier) {
        if (nomFichier == null) {
            return "";
        }
        String nom = nomFichier.replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1);
        int point = nom.lastIndexOf('.');
        return point < 0 ? "" : nom.substring(point + 1).toLowerCase(Locale.ROOT);
    }

    /** Nom d'origine assaini, entre guillemets, ou mention neutre s'il est absent. */
    private static String nomLisible(MultipartFile file) {
        String nom = file.getOriginalFilename();
        if (nom == null || nom.isBlank()) {
            return "envoyé";
        }
        String propre = nom.replace('\\', '/');
        propre = propre.substring(propre.lastIndexOf('/') + 1);
        return "« " + propre.replaceAll("[\\p{Cntrl}]", "") + " »";
    }

    private static String formatsLisibles(List<String> formats) {
        return formats.stream().map(f -> "." + f).reduce((a, b) -> a + ", " + b).orElse(".xlsx");
    }

    /** Taille en Mo avec une decimale, ou en Ko sous le megaoctet. */
    private static String tailleLisible(long octets) {
        if (octets < 1024 * 1024) {
            return String.format("%d Ko", Math.max(1, octets / 1024));
        }
        return String.format(Locale.FRANCE, "%.1f Mo", octets / (1024.0 * 1024.0));
    }
}
