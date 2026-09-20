package com.campusops.config;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.level.entity.Level;
import com.campusops.level.repository.LevelRepository;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Amorce un semestre « courant » par défaut pour chaque niveau/cycle — point de
 * départ de la bascule de semestre (§20).
 *
 * <p>Pour chaque niveau qui n'a <b>aucun</b> semestre courant, on marque comme
 * courant son semestre <b>actif d'ordre le plus bas</b> (S1 pour le Tronc
 * commun, S3 pour la Licence, S1 pour le Cycle ingénieur…). Les niveaux sans
 * semestre (Doctorat, Formation continue vide) sont naturellement ignorés.
 *
 * <p>Ce seeder ne pose qu'<b>un seul</b> courant par défaut ; un niveau qui
 * couvre plusieurs années (cohortes coexistantes) peut ensuite en avoir
 * <b>plusieurs</b> (ex. S1 + S3, ou S1 + S3 + S5) que l'administrateur ajoute
 * via l'UI. Aucune valeur n'est codée en dur (§26) : le choix repose sur l'ordre
 * des semestres réellement présents.
 *
 * <p><b>Idempotent et non destructif (§29)</b> : n'agit que sur les niveaux
 * dépourvus de courant ; ne touche jamais un courant déjà positionné (par ce
 * seeder, par l'administrateur via l'UI ou par une bascule précédente). Se
 * désactive via {@code campusops.seed.semester-courant=false}.
 *
 * <p>S'exécute <b>après</b> {@link AcademicStructureInitializer} (@Order(8)),
 * qui crée les semestres par cycle, et après {@link SemesterConstraintInitializer}
 * (@Order(2)), qui garantit la colonne 'courant' et les colonnes de période.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(15)
public class SemesterCurrentInitializer implements CommandLineRunner {

    private final LevelRepository levelRepository;
    private final SemesterRepository semesterRepository;
    private final AcademicYearRepository academicYearRepository;

    @Value("${campusops.seed.semester-courant:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[semester-courant] Amorçage du semestre courant désactivé "
                    + "(campusops.seed.semester-courant=false).");
            return;
        }

        // Bornes de reference pour dater les semestres courants : l'annee active.
        // Sert de fenetre de validite par defaut aux emplois du temps (§20).
        AcademicYear anneeActive = academicYearRepository.findFirstByActifTrue().orElse(null);

        int marques = 0;
        for (Level level : levelRepository.findAll()) {
            // Amorçage : ne semer un courant par défaut que si le niveau n'en a
            // encore AUCUN. Un niveau peut désormais avoir plusieurs semestres
            // courants (cohortes coexistantes) ; on ne touche donc pas à un niveau
            // déjà pourvu, et on n'écrase jamais une configuration existante.
            if (!semesterRepository.findByLevelIdAndCourantTrue(level.getId()).isEmpty()) {
                continue;
            }
            Semester premier = semesterRepository
                    .findFirstByLevelIdAndActifTrueOrderByOrdreAsc(level.getId())
                    .orElse(null);
            if (premier != null) {
                premier.setCourant(true);
                appliquerPeriodeParDefaut(premier, anneeActive);
                semesterRepository.save(premier);
                marques++;
                log.debug("[semester-courant] Niveau '{}' : semestre courant initialisé sur '{}'.",
                        level.getNom(), premier.getNom());
            }
        }

        if (marques > 0) {
            log.info("[semester-courant] Semestre courant initialisé pour {} niveau(x).", marques);
        }
    }

    /**
     * Dote un semestre courant d'une fenêtre de validité par défaut lorsqu'il n'en
     * a pas encore (§29 : on n'écrase jamais des dates déjà saisies). Les semestres
     * d'ordre impair (S1, S3, S5…) couvrent le 1er quadrimestre de l'année active
     * (début de l'année → 31/12) ; les semestres pairs couvrent le 2e (01/02 → fin
     * de l'année). Ces bornes servent de limites à l'import d'emploi du temps :
     * la date d'expiration choisie par le RP ne peut pas dépasser la fin du
     * semestre. L'administrateur peut ensuite les ajuster par niveau via l'UI.
     */
    private void appliquerPeriodeParDefaut(Semester semestre, AcademicYear anneeActive) {
        if (anneeActive == null || anneeActive.getDateDebut() == null
                || anneeActive.getDateFin() == null) {
            return;
        }
        if (semestre.getDateDebut() != null || semestre.getDateFin() != null) {
            return;
        }
        LocalDate debutAnnee = anneeActive.getDateDebut();
        LocalDate finAnnee = anneeActive.getDateFin();
        boolean premierQuadrimestre = semestre.getOrdre() != null
                && semestre.getOrdre() % 2 == 1;
        if (premierQuadrimestre) {
            semestre.setDateDebut(debutAnnee);
            semestre.setDateFin(LocalDate.of(debutAnnee.getYear(), 12, 31));
        } else {
            semestre.setDateDebut(LocalDate.of(finAnnee.getYear(), 2, 1));
            semestre.setDateFin(finAnnee);
        }
    }
}
