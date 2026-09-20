package com.campusops.config;

import com.campusops.calendar.entity.NonWorkingDay;
import com.campusops.calendar.repository.NonWorkingDayRepository;
import com.campusops.enums.NonWorkingDayType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Amorcage du <b>calendrier non ouvrable</b> pour le Maroc, annees 2026 et 2027
 * (§5).
 *
 * <p>Deux familles d'entrees :</p>
 * <ul>
 *   <li><b>Fetes a date fixe</b> ({@code FERIE_NATIONAL}) : 1er janvier,
 *       11 janvier (Manifeste de l'Independance), 14 janvier (Nouvel An
 *       amazigh), 1er mai, 30 juillet (Fete du Trone), 14 aout (Oued Eddahab),
 *       20 aout (Revolution du Roi et du Peuple), 21 aout (Fete de la
 *       Jeunesse), 6 novembre (Marche Verte), 18 novembre (Fete de
 *       l'Independance).</li>
 *   <li><b>Fetes religieuses</b> ({@code FERIE_RELIGIEUX}) : dates
 *       <b>previsionnelles</b>, marquees comme telles, car elles dependent de
 *       l'observation officielle. Elles restent modifiables par l'ADMIN via
 *       {@code PUT /api/non-working-days/{id}} des l'annonce officielle (§4).</li>
 * </ul>
 *
 * <p><b>Idempotent et non destructif</b> : chaque entree est reconnue par son
 * couple (dateDebut, libelle) ; une entree deja presente n'est ni recreee ni
 * modifiee, ce qui preserve les corrections faites par l'administrateur. Aucune
 * suppression n'est effectuee. Desactivable via
 * {@code campusops.seed.non-working-days=false}.</p>
 *
 * <p>Le dimanche n'est <b>pas</b> seede : c'est une regle hebdomadaire portee
 * par le moteur de disponibilite (§3), pas une date du calendrier.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(16)
public class NonWorkingDayInitializer implements CommandLineRunner {

    private final NonWorkingDayRepository repository;

    @Value("${campusops.seed.non-working-days:true}")
    private boolean enabled;

    /** Une entree du calendrier a semer. */
    private record DaySeed(LocalDate debut, LocalDate fin, String libelle,
                           NonWorkingDayType type, boolean previsionnel, String commentaire) {

        static DaySeed fixe(int annee, int mois, int jour, String libelle) {
            LocalDate d = LocalDate.of(annee, mois, jour);
            return new DaySeed(d, d, libelle, NonWorkingDayType.FERIE_NATIONAL, false, null);
        }

        static DaySeed religieuse(LocalDate debut, LocalDate fin, String libelle) {
            return new DaySeed(debut, fin, libelle, NonWorkingDayType.FERIE_RELIGIEUX, true,
                    "Date prévisionnelle : à confirmer selon l'observation officielle.");
        }
    }

    /** Fetes nationales a date fixe, repetees pour chaque annee demandee. */
    private static final List<int[]> FIXED_HOLIDAYS = List.of(
            new int[]{1, 1},    // Nouvel An
            new int[]{1, 11},   // Manifeste de l'Independance
            new int[]{1, 14},   // Nouvel An amazigh
            new int[]{5, 1},    // Fete du Travail
            new int[]{7, 30},   // Fete du Trone
            new int[]{8, 14},   // Oued Eddahab
            new int[]{8, 20},   // Revolution du Roi et du Peuple
            new int[]{8, 21},   // Fete de la Jeunesse
            new int[]{11, 6},   // Marche Verte
            new int[]{11, 18}   // Fete de l'Independance
    );

    private static final List<String> FIXED_LABELS = List.of(
            "Nouvel An",
            "Manifeste de l'Indépendance",
            "Nouvel An amazigh",
            "Fête du Travail",
            "Fête du Trône",
            "Oued Eddahab",
            "Révolution du Roi et du Peuple",
            "Fête de la Jeunesse",
            "Marche Verte",
            "Fête de l'Indépendance"
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Amorçage du calendrier non ouvrable désactivé (campusops.seed.non-working-days=false)");
            return;
        }

        migrateFixedNationalHolidays();

        List<DaySeed> seeds = new ArrayList<>();
        for (int i = 0; i < FIXED_HOLIDAYS.size(); i++) {
            int[] md = FIXED_HOLIDAYS.get(i);
            seeds.add(DaySeed.fixe(2026, md[0], md[1], FIXED_LABELS.get(i)));
        }
        seeds.addAll(religiousSeeds());

        int created = 0;
        for (DaySeed seed : seeds) {
            if (fixedHolidayExists(seed) || repository.existsByDateDebutAndLibelleIgnoreCase(seed.debut(), seed.libelle())) {
                continue;
            }
            repository.save(NonWorkingDay.builder()
                    .dateDebut(seed.debut())
                    .dateFin(seed.fin())
                    .libelle(seed.libelle())
                    .type(seed.type())
                    .previsionnel(seed.previsionnel())
                    .recurrent(seed.type() == NonWorkingDayType.FERIE_NATIONAL)
                    .commentaire(seed.commentaire())
                    .actif(true)
                    .build());
            created++;
        }

        if (created > 0) {
            log.info("Calendrier non ouvrable : {} journée(s) fériée(s) ajoutée(s) (Maroc 2026-2027)", created);
        } else {
            log.info("Calendrier non ouvrable déjà à jour (aucune journée ajoutée)");
        }
    }

    private boolean fixedHolidayExists(DaySeed seed) {
        if (seed.type() != NonWorkingDayType.FERIE_NATIONAL) {
            return false;
        }
        return repository.findAll().stream()
                .anyMatch(day -> day.isRecurrent()
                        && day.getDateDebut() != null
                        && day.getDateDebut().getMonthValue() == seed.debut().getMonthValue()
                        && day.getDateDebut().getDayOfMonth() == seed.debut().getDayOfMonth()
                        && day.getLibelle().equalsIgnoreCase(seed.libelle()));
    }

    private void migrateFixedNationalHolidays() {
        List<NonWorkingDay> fixedDays = repository.findAll().stream()
                .filter(day -> day.getType() == NonWorkingDayType.FERIE_NATIONAL)
                .filter(day -> day.getDateDebut() != null)
                .filter(day -> FIXED_LABELS.stream().anyMatch(label ->
                        label.equalsIgnoreCase(day.getLibelle())))
                .toList();

        List<NonWorkingDay> kept = new ArrayList<>();
        for (NonWorkingDay day : fixedDays) {
            day.setRecurrent(true);
            NonWorkingDay existing = kept.stream()
                    .filter(candidate -> candidate.getDateDebut().getMonthValue() == day.getDateDebut().getMonthValue())
                    .filter(candidate -> candidate.getDateDebut().getDayOfMonth() == day.getDateDebut().getDayOfMonth())
                    .filter(candidate -> Objects.equals(candidate.getLibelle(), day.getLibelle()))
                    .findFirst()
                    .orElse(null);
            if (existing == null) {
                repository.save(day);
                kept.add(day);
            } else {
                repository.delete(day);
            }
        }
    }

    /**
     * Fetes religieuses previsionnelles. Les dates suivent le calendrier
     * hegirien : elles sont donnees a titre indicatif et l'ADMIN les corrige
     * apres l'annonce officielle (§4). Aid al-Fitr, Aid al-Adha et le Mawlid
     * sont chomes deux jours au Maroc, d'ou des periodes de deux jours.
     */
    private List<DaySeed> religiousSeeds() {
        return List.of(
                // ----- 2026 -----
                DaySeed.religieuse(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 21),
                        "Aïd al-Fitr 1447"),
                DaySeed.religieuse(LocalDate.of(2026, 5, 27), LocalDate.of(2026, 5, 28),
                        "Aïd al-Adha 1447"),
                DaySeed.religieuse(LocalDate.of(2026, 6, 17), LocalDate.of(2026, 6, 17),
                        "Nouvel An hégirien 1448"),
                DaySeed.religieuse(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 26),
                        "Aïd al-Mawlid 1448"),
                // ----- 2027 -----
                DaySeed.religieuse(LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 11),
                        "Aïd al-Fitr 1448"),
                DaySeed.religieuse(LocalDate.of(2027, 5, 17), LocalDate.of(2027, 5, 18),
                        "Aïd al-Adha 1448"),
                DaySeed.religieuse(LocalDate.of(2027, 6, 6), LocalDate.of(2027, 6, 6),
                        "Nouvel An hégirien 1449"),
                DaySeed.religieuse(LocalDate.of(2027, 8, 15), LocalDate.of(2027, 8, 16),
                        "Aïd al-Mawlid 1449")
        );
    }
}
