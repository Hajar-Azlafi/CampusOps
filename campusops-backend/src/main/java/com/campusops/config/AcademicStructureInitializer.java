package com.campusops.config;

import com.campusops.academicyear.service.AcademicStructureService;
import com.campusops.academicyear.service.AcademicStructureService.SeedCounts;
import com.campusops.group.repository.GroupRepository;
import com.campusops.promotion.repository.PromotionRepository;
import com.campusops.semester.repository.SemesterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Structure académique idempotente au démarrage : semestres par cycle,
 * nettoyage des anciens semestres génériques, puis promotions/groupes initiaux
 * de l'année active. S'exécute APRÈS le reset du référentiel ({@code @Order(7)})
 * afin de travailler sur les 6 niveaux et les filières réelles définitives.
 *
 * <p>La logique métier est déléguée à {@link AcademicStructureService} (partagée
 * avec le passage à l'année suivante). Ici on ne fait qu'orchestrer et
 * journaliser, sous le contrôle d'un interrupteur général.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(8)
public class AcademicStructureInitializer implements CommandLineRunner {

    private final AcademicStructureService academicStructureService;
    private final SemesterRepository semesterRepository;
    private final PromotionRepository promotionRepository;
    private final GroupRepository groupRepository;

    /** Interrupteur général : permet de désactiver entièrement ce seed. */
    @Value("${campusops.seed.academic-structure:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Seed de structure académique désactivé (campusops.seed.academic-structure=false).");
            return;
        }

        int semestersCreated = academicStructureService.seedSemestersPerLevel();
        int legacyRemoved = academicStructureService.cleanupLegacyDemoSemesters();
        int misnumberedRemoved = academicStructureService.cleanupUnexpectedLevelSemesters();
        SeedCounts counts = academicStructureService.seedPromotionsAndGroupsForActiveYear();

        log.info("========================================");
        log.info("Structure académique (idempotente) :");
        log.info("  - {} semestre(s) par cycle créé(s)", semestersCreated);
        log.info("  - {} ancien(s) semestre(s) générique(s) nettoyé(s)", legacyRemoved);
        log.info("  - {} semestre(s) mal numéroté(s) non référencé(s) retiré(s)", misnumberedRemoved);
        log.info("  - {} promotion(s) créée(s) pour l'année active", counts.promotions());
        log.info("  - {} groupe(s) créé(s) pour l'année active", counts.groups());
        log.info("  - Totaux : {} semestres, {} promotions, {} groupes",
                semesterRepository.count(), promotionRepository.count(), groupRepository.count());
        log.info("========================================");
    }
}
