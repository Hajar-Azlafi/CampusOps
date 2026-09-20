package com.campusops.semester.service;

import com.campusops.academicyear.entity.AcademicYear;
import com.campusops.academicyear.repository.AcademicYearRepository;
import com.campusops.enums.TimetableStatus;
import com.campusops.exception.BadRequestException;
import com.campusops.level.entity.Level;
import com.campusops.semester.dto.SemesterAdvanceDto;
import com.campusops.semester.dto.SemesterRolloverResultDto;
import com.campusops.semester.entity.Semester;
import com.campusops.semester.repository.SemesterRepository;
import com.campusops.timetable.entity.EmploiDuTemps;
import com.campusops.timetable.repository.EmploiDuTempsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bascule « semestre suivant » (§20) — fait avancer chaque niveau au semestre
 * suivant de son cycle (S1→S2, S3→S4, S5→S6…) <b>sans rien supprimer</b>, dans
 * le même esprit que le passage à l'année suivante
 * ({@code AcademicYearRolloverService}).
 *
 * <p>Modèle « courants par niveau » : un niveau peut avoir <b>plusieurs</b>
 * semestres {@code courant} simultanés lorsqu'il couvre plusieurs années
 * (cohortes coexistantes : ex. S1 + S3, ou S1 + S3 + S5). Chaque courant avance
 * indépendamment vers le semestre actif suivant de son cycle. Le calcul se fait
 * sur un <b>instantané</b> des courants puis les changements sont appliqués, de
 * sorte qu'un semestre simultanément quitté par une cohorte et rejoint par une
 * autre (ex. S2→S3 pendant que S3→S4) reste correctement courant. Pour chaque
 * semestre effectivement quitté (retiré des courants), la bascule :
 * <ol>
 *   <li>retire le marqueur {@code courant} ;</li>
 *   <li>archive les emplois du temps de l'<b>année active</b> rattachés à ce
 *       semestre → statut {@code ARCHIVE}, ce qui libère automatiquement leurs
 *       salles (cf. F1, {@code timetableActiveOn}).</li>
 * </ol>
 * Les nouveaux semestres cibles sont marqués courants.
 *
 * <p>Les niveaux déjà au dernier semestre de leur cycle sont laissés inchangés.
 * L'archivage est borné à l'année universitaire active : l'historique des années
 * passées n'est jamais modifié.
 *
 * <p><b>Sécurité</b> : à l'image du passage d'année, ce service n'impose pas
 * lui-même le contrôle d'accès — le point d'entrée manuel (contrôleur) exige
 * ADMIN via {@code AccessScopeService.requireAdmin()}, tandis que le planificateur
 * opt-in appelle directement la méthode (hors contexte de sécurité).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SemesterRolloverService {

    private final SemesterRepository semesterRepository;
    private final EmploiDuTempsRepository emploiDuTempsRepository;
    private final AcademicYearRepository academicYearRepository;

    @Transactional
    public SemesterRolloverResultDto passerAuSemestreSuivant() {
        List<Semester> courants = semesterRepository.findByCourantTrue();
        if (courants.isEmpty()) {
            throw new BadRequestException(
                    "Aucun semestre courant n'est défini : impossible de basculer. "
                            + "Définissez d'abord le semestre courant d'au moins un niveau.");
        }

        // Année active : borne l'archivage aux emplois du temps de l'année en
        // cours (l'historique des années passées reste intact). Peut être
        // absente : on bascule alors les semestres sans rien archiver.
        AcademicYear anneeActive = academicYearRepository.findFirstByActifTrue().orElse(null);

        // Un niveau peut avoir PLUSIEURS courants (cohortes coexistantes). On
        // calcule d'abord, sur l'instantané des courants, la cible de chacun,
        // PUIS on applique — ainsi un même semestre à la fois quitté par une
        // cohorte et rejoint par une autre (ex. S2→S3 pendant que S3→S4) reste
        // courant et n'est pas archivé à tort.
        List<SemesterAdvanceDto> avancements = new ArrayList<>();
        List<String> auDernier = new ArrayList<>();

        // Ensemble cible des courants après bascule : suivant de chaque courant
        // qui en a un ; les courants sans suivant (dernier semestre) s'y maintiennent.
        Set<Long> nouveauxCourantsIds = new HashSet<>();
        for (Semester courant : courants) {
            Semester suivant = trouverSemestreActifSuivant(courant).orElse(null);
            if (suivant == null) {
                auDernier.add(libelleNiveau(courant));
                nouveauxCourantsIds.add(courant.getId()); // se maintient
                continue;
            }
            nouveauxCourantsIds.add(suivant.getId());
            Level niveau = courant.getLevel();
            avancements.add(SemesterAdvanceDto.builder()
                    .levelId(niveau != null ? niveau.getId() : null)
                    .levelNom(niveau != null ? niveau.getNom() : null)
                    .ancienSemestreId(courant.getId())
                    .ancienSemestreNom(courant.getNom())
                    .nouveauSemestreId(suivant.getId())
                    .nouveauSemestreNom(suivant.getNom())
                    .build());
        }

        int archives = 0;

        // 1) Retirer des courants les semestres de l'instantané qui NE figurent PAS
        //    dans le nouvel ensemble (un semestre simultanément rejoint reste courant),
        //    et archiver leurs emplois du temps de l'année active (salles libérées).
        for (Semester courant : courants) {
            if (!nouveauxCourantsIds.contains(courant.getId())) {
                courant.setCourant(false);
                semesterRepository.save(courant);
                if (anneeActive != null) {
                    archives += archiverEmploisDuTemps(anneeActive.getId(), courant.getId());
                }
            }
        }

        // 2) Marquer les nouveaux courants (idempotent pour ceux déjà courants).
        for (Long id : nouveauxCourantsIds) {
            semesterRepository.findById(id).ifPresent(s -> {
                if (!Boolean.TRUE.equals(s.getCourant())) {
                    s.setCourant(true);
                    semesterRepository.save(s);
                }
            });
        }

        String message = avancements.isEmpty()
                ? "Aucun niveau n'a avancé : tous les semestres courants sont déjà au "
                        + "dernier semestre de leur cycle."
                : avancements.size() + " avancement(s) de semestre effectué(s), "
                        + archives + " emploi(s) du temps archivé(s) (salles libérées).";
        log.info("[semester-rollover] {}", message);

        return SemesterRolloverResultDto.builder()
                .avancements(avancements)
                .semestresAuDernier(auDernier)
                .emploisDuTempsArchives(archives)
                .message(message)
                .build();
    }

    /**
     * Semestre actif suivant (ordre strictement supérieur) dans le même niveau,
     * ou parmi les semestres sans niveau si le courant n'en a pas.
     */
    private Optional<Semester> trouverSemestreActifSuivant(Semester courant) {
        Integer ordre = courant.getOrdre();
        Level niveau = courant.getLevel();
        if (niveau != null) {
            return semesterRepository
                    .findFirstByLevelIdAndActifTrueAndOrdreGreaterThanOrderByOrdreAsc(
                            niveau.getId(), ordre);
        }
        return semesterRepository
                .findFirstByLevelIsNullAndActifTrueAndOrdreGreaterThanOrderByOrdreAsc(ordre);
    }

    /**
     * Archive (statut {@code ARCHIVE}) les emplois du temps non déjà archivés de
     * l'année et du semestre donnés ; renvoie le nombre d'emplois du temps
     * archivés. L'archivage libère les salles (cf. F1).
     */
    private int archiverEmploisDuTemps(Long academicYearId, Long semesterId) {
        List<EmploiDuTemps> aArchiver = emploiDuTempsRepository
                .findByAcademicYearIdAndSemesterIdAndStatutNot(
                        academicYearId, semesterId, TimetableStatus.ARCHIVE);
        if (aArchiver.isEmpty()) {
            return 0;
        }
        for (EmploiDuTemps edt : aArchiver) {
            edt.setStatut(TimetableStatus.ARCHIVE);
        }
        emploiDuTempsRepository.saveAll(aArchiver);
        return aArchiver.size();
    }

    private String libelleNiveau(Semester semestre) {
        String niveau = (semestre.getLevel() != null) ? semestre.getLevel().getNom() : "Sans niveau";
        return niveau + " (" + semestre.getNom() + ")";
    }
}
