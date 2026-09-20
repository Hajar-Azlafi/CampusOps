package com.campusops.semester.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Détail d'un avancement de semestre pour un niveau donné, produit par la
 * bascule de semestre (§20) : le niveau passe de {@code ancienSemestre} au
 * {@code nouveauSemestre} (S1→S2, S3→S4…).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemesterAdvanceDto {

    /** Niveau concerné (null pour les semestres sans niveau, Formation continue). */
    private Long levelId;
    private String levelNom;

    /** Semestre quitté (celui qui était courant avant la bascule). */
    private Long ancienSemestreId;
    private String ancienSemestreNom;

    /** Nouveau semestre courant après la bascule. */
    private Long nouveauSemestreId;
    private String nouveauSemestreNom;
}
