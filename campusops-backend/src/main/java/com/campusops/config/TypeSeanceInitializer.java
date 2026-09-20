package com.campusops.config;

import com.campusops.enums.SessionType;
import com.campusops.schedule.entity.Schedule;
import com.campusops.schedule.repository.ScheduleRepository;
import com.campusops.typeseance.entity.TypeSeance;
import com.campusops.typeseance.repository.TypeSeanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Amorçage des <b>types de séance configurables</b> (cahier des charges §7) et
 * <b>migration non destructive</b> de l'ancienne énumération
 * {@link com.campusops.enums.SessionType} vers l'entité
 * {@link com.campusops.typeseance.entity.TypeSeance} (§22-§23).
 *
 * <p>Sept types canoniques sont amorcés — <b>génériques</b> (§14), aucun libellé
 * propre à un établissement n'est codé en dur :</p>
 * <pre>
 *   Ordre 1 : Cours       (COURS)
 *   Ordre 2 : TD          (TD)
 *   Ordre 3 : TP          (TP)
 *   Ordre 4 : Examen      (EXAMEN)
 *   Ordre 5 : Contrôle    (CONTROLE)
 *   Ordre 6 : Soutenance  (SOUTENANCE)
 *   Ordre 7 : Autre       (AUTRE)
 * </pre>
 *
 * <p><b>Idempotent</b> : chaque type est reconnu par son code ; on ne recrée
 * jamais un type déjà présent. <b>Rétro-remplissage</b> : chaque séance
 * existante dépourvue de FK reçoit le {@link TypeSeance} dont le code correspond
 * à son enum ({@code COURS}, {@code TD}, {@code TP}, {@code EXAMEN}, {@code AUTRE}),
 * avec repli sur « Autre » si le code est inconnu. <b>Non destructif</b> : la
 * colonne enum {@code type} est conservée comme source historique.</p>
 *
 * <p>S'exécute <b>après</b> {@code TimetableSeedInitializer} ({@code @Order(11)})
 * afin que toutes les séances de démonstration existent avant le
 * rétro-remplissage.</p>
 */
@Component
@Order(12)
@RequiredArgsConstructor
@Slf4j
public class TypeSeanceInitializer implements CommandLineRunner {

    private final TypeSeanceRepository typeSeanceRepository;
    private final ScheduleRepository scheduleRepository;

    @Value("${campusops.seed.seance-types:true}")
    private boolean enabled;

    /** Un type canonique : libellé, code unique, couleur d'affichage, ordre. */
    private record TypeSeed(String nom, String code, String couleur, int ordre) {
    }

    /** Les 7 types de séance par défaut (§7). */
    private static final List<TypeSeed> DEFAULT_TYPES = List.of(
            new TypeSeed("Cours", "COURS", "#2563EB", 1),
            new TypeSeed("TD", "TD", "#16A34A", 2),
            new TypeSeed("TP", "TP", "#9333EA", 3),
            new TypeSeed("Examen", "EXAMEN", "#DC2626", 4),
            new TypeSeed("Contrôle", "CONTROLE", "#EA580C", 5),
            new TypeSeed("Soutenance", "SOUTENANCE", "#0891B2", 6),
            new TypeSeed("Autre", "AUTRE", "#64748B", 7)
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Amorçage des types de séance désactivé "
                    + "(campusops.seed.seance-types=false).");
            return;
        }

        int created = seedTypes();
        int backfilled = backfillSchedules();

        log.info("Types de séance de référence : {} créé(s), {} au total ; "
                        + "{} séance(s) rétro-associée(s) à un type.",
                created, typeSeanceRepository.count(), backfilled);
    }

    /** Amorce les 7 types canoniques (idempotent par code). */
    private int seedTypes() {
        int created = 0;
        for (TypeSeed seed : DEFAULT_TYPES) {
            if (typeSeanceRepository.existsByCode(seed.code())) {
                continue; // déjà présent : idempotent, aucune modification.
            }
            typeSeanceRepository.save(TypeSeance.builder()
                    .nom(seed.nom())
                    .code(seed.code())
                    .couleur(seed.couleur())
                    .ordre(seed.ordre())
                    .actif(true)
                    .build());
            created++;
            log.info("Type de séance de référence créé : {} ({}).", seed.nom(), seed.code());
        }
        return created;
    }

    /**
     * Rétro-remplit {@code Schedule.typeSeance} pour les séances qui n'en ont pas
     * encore, en associant le type dont le code correspond au nom de l'enum
     * historique. Repli sur « Autre » si le code est absent ou inconnu.
     */
    private int backfillSchedules() {
        TypeSeance fallback = typeSeanceRepository.findByCode(SessionType.AUTRE.name()).orElse(null);
        int backfilled = 0;
        for (Schedule schedule : scheduleRepository.findAll()) {
            if (schedule.getTypeSeance() != null) {
                continue; // déjà associé : non destructif.
            }
            SessionType enumType = schedule.getType();
            String code = (enumType != null) ? enumType.name() : SessionType.AUTRE.name();
            TypeSeance type = typeSeanceRepository.findByCode(code).orElse(fallback);
            if (type != null) {
                schedule.setTypeSeance(type);
                scheduleRepository.save(schedule);
                backfilled++;
            }
        }
        return backfilled;
    }
}
