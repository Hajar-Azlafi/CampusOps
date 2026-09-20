package com.campusops.config;

import com.campusops.timeslot.entity.TimeSlot;
import com.campusops.timeslot.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/**
 * Amorçage des <b>créneaux horaires de référence</b> de l'université (cahier des
 * charges §1.1). Ces créneaux deviennent la référence utilisée partout dans
 * CampusOps (emplois du temps, ajout/modification de séance, disponibilités,
 * filtres).
 *
 * <p>Créneaux par défaut (§1.1) — le <b>dernier se termine impérativement à
 * 18:00</b>, pas 18:15 :</p>
 * <pre>
 *   Ordre 1 : 08:30 → 10:25
 *   Ordre 2 : 10:35 → 12:30
 *   Ordre 3 : 14:00 → 15:55
 *   Ordre 4 : 16:05 → 18:00
 * </pre>
 *
 * <p><b>Idempotent</b> : chaque créneau est reconnu par son couple
 * (heureDebut, heureFin) ; on ne recrée jamais un créneau déjà présent, on se
 * contente de fixer son {@code ordre} et son {@code nom} si absents.
 * <b>Non destructif</b> (§23) : aucun créneau existant n'est supprimé. Les
 * créneaux « hors grille » déjà présents (référencés par d'anciennes séances)
 * sont conservés et reçoivent un {@code ordre} pour rester affichables ;
 * l'administrateur peut ensuite les désactiver.</p>
 *
 * <p>S'exécute <b>avant</b> {@code AcademicInitializer} ({@code @Order(6)}) afin
 * que les séances de démonstration référencent ces créneaux canoniques.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(5)
public class TimeSlotInitializer implements CommandLineRunner {

    private final TimeSlotRepository timeSlotRepository;

    @Value("${campusops.seed.time-slots:true}")
    private boolean enabled;

    /** Un créneau canonique : ordre d'affichage + bornes horaires. */
    private record SlotSeed(int ordre, LocalTime debut, LocalTime fin) {
    }

    /** Les 4 créneaux par défaut (§1.1). Dernier = 18:00. */
    private static final List<SlotSeed> DEFAULT_SLOTS = List.of(
            new SlotSeed(1, LocalTime.of(8, 30), LocalTime.of(10, 25)),
            new SlotSeed(2, LocalTime.of(10, 35), LocalTime.of(12, 30)),
            new SlotSeed(3, LocalTime.of(14, 0), LocalTime.of(15, 55)),
            new SlotSeed(4, LocalTime.of(16, 5), LocalTime.of(18, 0))
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Amorçage des créneaux horaires désactivé (campusops.seed.time-slots=false).");
            return;
        }

        int created = 0;
        for (SlotSeed seed : DEFAULT_SLOTS) {
            TimeSlot slot = timeSlotRepository
                    .findByHeureDebutAndHeureFin(seed.debut(), seed.fin())
                    .orElse(null);
            if (slot == null) {
                timeSlotRepository.save(TimeSlot.builder()
                        .nom("Créneau " + seed.ordre())
                        .heureDebut(seed.debut())
                        .heureFin(seed.fin())
                        .ordre(seed.ordre())
                        .actif(true)
                        .build());
                created++;
            } else {
                // Déjà présent : on fixe seulement l'ordre/nom si absents (idempotent).
                boolean dirty = false;
                if (slot.getOrdre() == null) {
                    slot.setOrdre(seed.ordre());
                    dirty = true;
                }
                if (slot.getNom() == null || slot.getNom().isBlank()) {
                    slot.setNom("Créneau " + seed.ordre());
                    dirty = true;
                }
                if (dirty) {
                    timeSlotRepository.save(slot);
                }
            }
        }

        backfillMissingOrdre();

        log.info("Créneaux horaires de référence : {} créé(s), {} au total.",
                created, timeSlotRepository.count());
    }

    /**
     * Rétro-remplit l'{@code ordre} des créneaux « hors grille » qui n'en ont
     * pas encore (anciennes données), en poursuivant la numérotation après le
     * plus grand ordre déjà attribué, par heure de début croissante.
     */
    private void backfillMissingOrdre() {
        List<TimeSlot> all = timeSlotRepository.findAll();
        int maxOrdre = all.stream()
                .map(TimeSlot::getOrdre)
                .filter(o -> o != null)
                .max(Integer::compareTo)
                .orElse(0);
        List<TimeSlot> sansOrdre = all.stream()
                .filter(t -> t.getOrdre() == null)
                .sorted(Comparator.comparing(TimeSlot::getHeureDebut))
                .toList();
        for (TimeSlot slot : sansOrdre) {
            slot.setOrdre(++maxOrdre);
            timeSlotRepository.save(slot);
        }
    }
}
