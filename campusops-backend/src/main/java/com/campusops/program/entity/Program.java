package com.campusops.program.entity;

import com.campusops.department.entity.Department;
import com.campusops.level.entity.Level;
import com.campusops.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "programs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_program_nom_level",
                columnNames = {"nom", "level_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"department", "level", "responsable"})
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nom de la filiere. Unique par cycle (contrainte composite nom + level_id)
     * afin qu'une meme filiere puisse exister dans plusieurs cycles
     * (ex. « Genie Informatique » en Tronc commun, Licence et Cycle ingenieur).
     */
    @Column(nullable = false)
    private String nom;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Etat <b>ACTIF / INACTIF</b> de la filiere (desactivation logique). Une
     * filiere inactive reste en base et reste consultable dans l'historique,
     * mais n'est plus proposee ni acceptee dans une nouvelle operation
     * (creation de promotion ou de groupe, module, emploi du temps, examen,
     * reservation, import Excel).
     *
     * <p><b>Migration non destructive</b> : colonne <b>nullable</b> ajoutee via
     * {@code ddl-auto=update} (le meme procede que {@code Semester.courant}).
     * Les filieres existantes prennent la valeur NULL, interpretee comme
     * <b>ACTIF</b> par {@link #isActif()}, puis normalisee a {@code true} par
     * {@link com.campusops.config.ProgramConstraintInitializer}.
     *
     * <p>L'acces se fait uniquement par {@link #isActif()} /
     * {@link #setActif(boolean)} : les accesseurs Lombok sont neutralises pour
     * qu'aucun appelant (ni MapStruct) ne manipule le {@code Boolean} brut et
     * risque un NPE sur une ligne non encore normalisee.
     */
    @Column(name = "actif")
    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private Boolean actif = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Niveau/cycle de rattachement de la filiere (Tronc commun, Licence,
     * Master, Cycle ingenieur, Doctorat, Formation continue). Nullable afin
     * de rester compatible avec d'eventuelles filieres existantes.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "level_id")
    private Level level;

    /**
     * Responsable pedagogique de la filiere (optionnel). Un responsable peut
     * gerer plusieurs filieres ; une filiere a au plus un responsable. Nullable
     * afin de rester compatible avec les filieres existantes (non destructif).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsable_id")
    private User responsable;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Etat actif de la filiere. NULL (ligne anterieure a l'ajout de la colonne,
     * pas encore normalisee) est traite comme <b>ACTIF</b> : la migration ne
     * doit jamais rendre une filiere existante inutilisable.
     */
    public boolean isActif() {
        return !Boolean.FALSE.equals(actif);
    }

    public void setActif(boolean actif) {
        this.actif = actif;
    }
}
