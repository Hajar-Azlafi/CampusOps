package com.campusops.exam.entity;

import com.campusops.academicsession.entity.SessionUniversitaire;
import com.campusops.enums.OccupationType;
import com.campusops.group.entity.Group;
import com.campusops.module.entity.Module;
import com.campusops.occupation.entity.OccupationSupplementaire;
import com.campusops.promotion.entity.Promotion;
import com.campusops.semester.entity.Semester;
import com.campusops.space.entity.Space;
import com.campusops.timeslot.entity.TimeSlot;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Examen daté (cahier des charges — module « Emplois du temps », Lot 2 F2), vu
 * comme une <strong>occupation supplémentaire</strong> spécialisée.
 *
 * <p>Contrairement à une {@link com.campusops.schedule.entity.Schedule séance}
 * qui est <em>récurrente</em> (un jour de la semaine, répété sur toute la période
 * de l'emploi du temps), un examen est un <strong>événement daté</strong> : il se
 * tient à une <em>date précise</em>, sur un {@link TimeSlot créneau} configuré,
 * dans une {@link Space salle} donnée. Il modélise aussi bien la
 * <em>session normale</em> que la <em>session de rattrapage</em> via la
 * {@link SessionUniversitaire session universitaire} (normale / rattrapage).</p>
 *
 * <p><strong>Héritage (restructuration « Occupation supplémentaire »)</strong> :
 * la date, les heures, l'espace, la filière, la promotion, le groupe, l'année,
 * le commentaire et l'indicateur d'activité sont désormais portés par
 * {@link OccupationSupplementaire}, socle commun aux examens, aux soutenances et
 * aux autres occupations ponctuelles. L'examen n'ajoute que son
 * <em>contexte pédagogique complet</em> : matière, semestre, session
 * universitaire et créneau officiel. Conséquence directe : le moteur central de
 * disponibilité voit un examen exactement comme n'importe quelle autre
 * occupation, sans code spécifique.</p>
 *
 * <p><strong>Public concerné</strong> : un examen cible soit un
 * {@link Group groupe} précis, soit — lorsque {@code group} est
 * <em>nul</em> — la {@link Promotion promotion} entière (champ hérité,
 * volontairement nullable).</p>
 *
 * <p><strong>Contexte dérivé</strong> : la {@link Module matière} porte la filière
 * et le semestre ; la promotion porte la filière, le niveau et l'année. Le service
 * vérifie la cohérence (module.filière == promotion.filière) puis fige
 * {@code program}, {@code semester} et {@code academicYear} à la création.</p>
 *
 * <p><strong>Non destructif</strong> (§29) : les colonnes obligatoires de
 * l'examen deviennent nullables en base puisqu'elles sont partagées avec les
 * autres types d'occupation ; leur caractère obligatoire reste garanti par
 * {@code ExamenService} (résolution + contrôle de cohérence systématiques).</p>
 */
@Entity
@DiscriminatorValue(OccupationSupplementaire.DISCRIMINATOR_EXAMEN)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"timeSlot", "module", "semester", "session"})
public class Examen extends OccupationSupplementaire {

    /**
     * Créneau horaire configuré (§3) portant les bornes [heureDebut, heureFin).
     * On réutilise le référentiel des créneaux, comme les séances, pour rester
     * cohérent avec la grille officielle. Les heures effectives de l'occupation
     * sont recopiées depuis ce créneau (voir {@link #syncOccupationFields()}).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    /** Matière évaluée. Porte la filière + le semestre (contexte pédagogique). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id")
    private Module module;

    /** Semestre (dérivé de la matière). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    /** Session universitaire : normale ou rattrapage. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private SessionUniversitaire session;

    /**
     * Garantit les invariants du socle générique quel que soit le chemin de
     * création (service, import, seeder) :
     * <ul>
     *   <li>{@code type} vaut toujours {@link OccupationType#EXAMEN} ;</li>
     *   <li>les heures de l'occupation recopient les bornes du créneau, ce qui
     *       permet au moteur de disponibilité de raisonner uniformément sur
     *       {@code heure_debut}/{@code heure_fin} sans jointure.</li>
     * </ul>
     */
    @PrePersist
    @PreUpdate
    void syncOccupationFields() {
        setType(OccupationType.EXAMEN);
        if (timeSlot != null) {
            setHeureDebut(timeSlot.getHeureDebut());
            setHeureFin(timeSlot.getHeureFin());
        }
    }
}
